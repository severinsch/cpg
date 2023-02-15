/*
 * Copyright (c) 2023, Fraunhofer AISEC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */
package de.fraunhofer.aisec.cpg.helper

import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.TranslationManager
import de.fraunhofer.aisec.cpg.frontends.java.JavaLanguage
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.expressions.Expression
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import org.junit.jupiter.api.*

data class TestInput(
    val name: String,
    val files: List<File>,
    val subdirectory: String = "",
    val nodeName: String? = null,
    val lineNumber: Int? = null,
    val hotspotType: StringPropertyHotspots.HotspotType? = null,
    val analyzeAllHotspots: Boolean = false,
)

data class TestResult(
    val name: String,
    val error: Boolean,
    val node: Expression?,
    val result: TestData?,
)

data class TestData(
    val grammarCreationDuration: Duration,
    val grammarSize: Pair<Int, Int>, // (nonterminals, productions)
    val charsetApproxDuration: Duration,
    val regularApproxDuration: Duration,
    val approximatedGrammarSize: Pair<Int, Int>, // (nonterminals, productions)
    val automatonCreationDuration: Duration,
    val automatonSize: Pair<Int, Int>, // (states, edges)
    val toRegexDuration: Duration,
    val regexSize: Int,
    val totalDuration: Duration =
        grammarCreationDuration +
            charsetApproxDuration +
            regularApproxDuration +
            automatonCreationDuration +
            toRegexDuration,
    val regex: String,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BenchmarkTest {
    @Test
    fun juliet() {
        val topPath =
            Path.of(
                "..",
                "..",
                "juliet_java_testsuite",
                "src",
                "testcases",
                "CWE89_SQL_Injection",
            )
        fun getInputs(subdir: String): List<TestInput> {
            val path = topPath.resolve(subdir)
            val testCases =
                path
                    .listDirectoryEntries("*.java")
                    .groupBy(
                        { f ->
                            f.toFile()
                                .nameWithoutExtension
                                .substringAfter("CWE89_SQL_Injection__")
                                .replace("G2B", "")
                                .replace("B2G", "")
                                .dropLastWhile { !it.isDigit() }
                        },
                        { f -> f.toFile() }
                    )
            val inputs =
                testCases.entries.map { (key, files) ->
                    TestInput(
                        name = "Juliet SQL Injection $key",
                        files = files,
                        hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                        analyzeAllHotspots = true,
                        subdirectory = subdir,
                    )
                }
            return inputs
        }

        val inputs = listOf("s01", "s02", "s03", "s04").flatMap { getInputs(it) }.take(1)

        println("Got ${inputs.size} test cases totaling ${inputs.sumOf { it.files.size }} files")
        val results = performBenchmarks(inputs, topPath, printResults = true)
        println("Got ${results.size} results")

        FileOutputStream("juliet_benchmarks.csv").use { it.writeCsv(results) }
    }

    @Test
    fun fixedExamples() {
        val path = Path.of("src", "test", "resources", "string_properties_benchmarks")
        val inputs =
            listOf(
                TestInput(
                    name = "Tricky",
                    files = listOf("Tricky.java").map { path.resolve(it).toFile() },
                    nodeName = "res",
                    lineNumber = 24,
                ),
                TestInput(
                    name = "StringProperties",
                    files = listOf("StringProperties.java").map { path.resolve(it).toFile() },
                    nodeName = "res",
                    lineNumber = 14,
                ),
                // Juliet SQL Injection flaw, bad source string concatenated into SQL query => .* in
                // result
                TestInput(
                    name = "Juliet SQL Injection prepareStatement 61",
                    files =
                        listOf(
                                "CWE89_SQL_Injection__database_prepareStatement_61a.java",
                                "CWE89_SQL_Injection__database_prepareStatement_61b.java"
                            )
                            .map { path.resolve("juliet_sql_61").resolve(it).toFile() },
                    subdirectory = "juliet_sql_61",
                    lineNumber = 40,
                    hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                ),
                // similar to 61, but different data flow
                TestInput(
                    name = "Juliet SQL Injection prepareStatement 68 bad sink",
                    files =
                        listOf(
                                "CWE89_SQL_Injection__database_prepareStatement_68a.java",
                                "CWE89_SQL_Injection__database_prepareStatement_68b.java"
                            )
                            .map { path.resolve("juliet_sql_68").resolve(it).toFile() },
                    subdirectory = "juliet_sql_68",
                    lineNumber = 40,
                    hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                ),
                // good version of above, bad source string not concatenated into SQL query => no .*
                // in
                // result
                TestInput(
                    name = "Juliet SQL Injection prepareStatement 68 bad source good sink",
                    files =
                        listOf(
                                "CWE89_SQL_Injection__database_prepareStatement_68a.java",
                                "CWE89_SQL_Injection__database_prepareStatement_68b.java"
                            )
                            .map { path.resolve("juliet_sql_68").resolve(it).toFile() },
                    subdirectory = "juliet_sql_68",
                    lineNumber = 156,
                    hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                ),
                // similar to others, but more complex data flow
                TestInput(
                    name = "Juliet SQL Injection prepareStatement 54",
                    files =
                        ('a'..'e')
                            .map { "CWE89_SQL_Injection__database_prepareStatement_54$it.java" }
                            .map { path.resolve("juliet_sql_54").resolve(it).toFile() },
                    subdirectory = "juliet_sql_54",
                    lineNumber = 39,
                    hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                ),
            )
        performBenchmarks(inputs, path)
    }

    private fun performBenchmarks(
        inputs: List<TestInput>,
        path: Path,
        printResults: Boolean = true
    ): List<TestResult> {
        val results: MutableList<TestResult> = mutableListOf()

        for (input in inputs) {
            StringPropertyHotspots.clear()
            buildCPG(input.files, path, input.subdirectory)

            if (input.analyzeAllHotspots) {
                println("Analyzing all hotspots for ${input.name}")
                val hotspots =
                    when (input.hotspotType) {
                        StringPropertyHotspots.HotspotType.DATABASE ->
                            StringPropertyHotspots.database_hotspots
                        StringPropertyHotspots.HotspotType.PRINT ->
                            StringPropertyHotspots.print_hotspots
                        StringPropertyHotspots.HotspotType.RETURN ->
                            StringPropertyHotspots.return_hotspots
                        else -> StringPropertyHotspots.hotspots
                    }
                for (hotspot in hotspots) {
                    val data = analyzeNode(hotspot)
                    results +=
                        TestResult(
                            "${input.name}[node ${hotspot.id} at ${hotspot.location}]",
                            false,
                            hotspot,
                            data
                        )
                }
            } else {
                val node =
                    StringPropertyHotspots.hotspots.find { expr ->
                        input.nodeName?.equals(expr.name)
                            ?: true &&
                            input.lineNumber?.equals(expr.location?.region?.startLine) ?: true &&
                            input.hotspotType?.let {
                                when (it) {
                                    StringPropertyHotspots.HotspotType.DATABASE ->
                                        expr in StringPropertyHotspots.database_hotspots
                                    StringPropertyHotspots.HotspotType.PRINT ->
                                        expr in StringPropertyHotspots.print_hotspots
                                    StringPropertyHotspots.HotspotType.RETURN ->
                                        expr in StringPropertyHotspots.return_hotspots
                                    StringPropertyHotspots.HotspotType.UNDETERMINED -> false
                                }
                            }
                                ?: true
                    }
                if (node == null) {
                    results += TestResult(input.name, true, null, null)
                    println("Could not find node for input $input")
                    continue
                }

                val data = analyzeNode(node)
                results += TestResult(input.name, false, node, data)
            }
        }
        if (printResults) {
            printTestResults(results)
        }
        StringPropertyHotspots.clear()
        return results
    }

    private fun buildCPG(
        files: List<File>,
        path: Path,
        subdir: String
    ): TranslationUnitDeclaration {
        TranslationManager.builder().build().analyze()
        val fullPath = path.resolve(subdir)
        val tu =
            TestUtils.analyzeAndGetFirstTU(files, fullPath, true) {
                it.registerLanguage<JavaLanguage>()
                    .registerPass(IdentifierPass())
                    .registerPass(EdgeCachePass())
                    .registerPass(StringPropertyPass())
            }
        return tu
    }

    @OptIn(ExperimentalTime::class)
    fun analyzeNode(node: Expression): TestData {
        println("Analyzing node ${node.name} at ${node.location}")
        val (grammar, grammarCreationDuration) = measureTimedValue { createGrammar(node) }

        val originalGrammarSize =
            grammar.getAllNonterminals().size to
                grammar.getAllNonterminals().flatMap { it.productions }.size

        val charsetApproxDuration = measureTime { CharSetApproximation(grammar).approximate() }
        val regularApproxDuration = measureTime { RegularApproximation(grammar).approximate() }

        val regularGrammarSize =
            grammar.getAllNonterminals().size to
                grammar.getAllNonterminals().flatMap { it.productions }.size

        val (automaton, automatonCreationDuration) =
            measureTimedValue { GrammarToNFA(grammar).makeFA() }

        val (pattern, toRegexDuration) = measureTimedValue { automaton.toRegex() }
        return TestData(
            grammarCreationDuration = grammarCreationDuration,
            grammarSize = originalGrammarSize,
            charsetApproxDuration = charsetApproxDuration,
            regularApproxDuration = regularApproxDuration,
            approximatedGrammarSize = regularGrammarSize,
            automatonCreationDuration = automatonCreationDuration,
            automatonSize =
                automaton.states.size to automaton.states.flatMap { it.outgoingEdges }.size,
            toRegexDuration = toRegexDuration,
            regexSize = pattern.length,
            regex = pattern,
        )
    }

    private fun printTestResults(results: List<TestResult>) {
        val ansiReset = "\u001B[0m"
        val ansiRed = "\u001B[31m"
        val ansiGreen = "\u001B[32m"

        val formatDuration = { duration: Duration, total: Duration ->
            "$duration (${String.format("%.2f", duration / total * 100)}% of total)"
        }

        println("Test results:")
        for (result in results) {
            if (result.error) {
                println("+++++++++++++++++++++++++++++")
                println("${ansiRed}Error in test ${result.name}$ansiReset")
                continue
            }
            val data = result.result!!
            println(
                """
                +++++++++++++++++++++++++++++
                ${ansiGreen}Test "${result.name}"$ansiReset:
                Grammar creation time:     ${formatDuration(data.grammarCreationDuration, data.totalDuration)}
                Grammar size:              ${data.grammarSize.first} nonterminals, ${data.grammarSize.second} productions
                Charset approximation:     ${formatDuration(data.charsetApproxDuration, data.totalDuration)}
                Regular approximation:     ${formatDuration(data.regularApproxDuration, data.totalDuration)}
                Approximated grammar size: ${data.approximatedGrammarSize.first} nonterminals, ${data.approximatedGrammarSize.second} productions
                Automaton creation:        ${formatDuration(data.automatonCreationDuration, data.totalDuration)}
                Automaton size:            ${data.automatonSize.first} states, ${data.automatonSize.second} edges
                Regex creation:            ${formatDuration(data.toRegexDuration, data.totalDuration)}
                Regex size:                ${data.regexSize}
                Total time:                ${data.totalDuration}
                Regex:                     ${prettyPrintPattern(data.regex)}
                """.trimIndent()
            )
        }
        println("+++++++++++++++++++++++++++++")
    }

    private fun OutputStream.writeCsv(results: List<TestResult>) {
        val header =
            """"Test name", "Error", "Grammar creation time [μs]", "Charset approximation time [μs]", "Regular approximation time [μs]", "Automaton creation time [μs]", "Regex creation time [μs]", "Total time [μs]", "Original grammar nonterminals", "Original grammar productions", "Approximated grammar nonterminals", "Approximated grammar productions", "Automaton nodes", "Automaton edges", "Regex size", "Regex""""
        val writer = this.bufferedWriter()
        writer.write(header)
        writer.newLine()
        results.forEach {
            writer.write(
                "\"${it.name}\", ${it.error}, ${it.result?.grammarCreationDuration?.inWholeMicroseconds}, ${it.result?.charsetApproxDuration?.inWholeMicroseconds}, ${it.result?.regularApproxDuration?.inWholeMicroseconds}, ${it.result?.automatonCreationDuration?.inWholeMicroseconds}, ${it.result?.toRegexDuration?.inWholeMicroseconds}, ${it.result?.totalDuration?.inWholeMicroseconds}, ${it.result?.grammarSize?.first}, ${it.result?.grammarSize?.second}, ${it.result?.approximatedGrammarSize?.first}, ${it.result?.approximatedGrammarSize?.second}, ${it.result?.automatonSize?.first}, ${it.result?.automatonSize?.second}, ${it.result?.regexSize}, ${it.result?.regex}"
            )
            writer.newLine()
        }
        writer.flush()
    }
}
