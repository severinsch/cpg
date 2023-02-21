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
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.frontends.java.JavaLanguage
import de.fraunhofer.aisec.cpg.graph.statements.expressions.Expression
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.io.path.deleteExisting
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
    val createDFA: Boolean = false,
)

data class TestResult(
    val name: String,
    val error: Boolean,
    val result: TestData?,
) {
    fun toCsvRow(): String =
        "\"${this.name}\",${this.error},${this.result?.grammarCreationDuration?.inWholeMicroseconds},${this.result?.charsetApproxDuration?.inWholeMicroseconds},${this.result?.regularApproxDuration?.inWholeMicroseconds},${this.result?.automatonCreationDuration?.inWholeMicroseconds},${this.result?.dfaCreationDuration?.inWholeMicroseconds},${this.result?.toRegexDuration?.inWholeMicroseconds},${this.result?.totalDuration?.inWholeMicroseconds},${this.result?.grammarSize?.first},${this.result?.grammarSize?.second},${this.result?.approximatedGrammarSize?.first},${this.result?.approximatedGrammarSize?.second},${this.result?.automatonSize?.first},${this.result?.automatonSize?.second},${this.result?.dfaSize?.first},${this.result?.dfaSize?.second},${this.result?.regexSize},\"${this.result?.regex}\"\n"

    companion object {
        const val csvHeader =
            "Test name,Error,Grammar creation time [μs],Charset approximation time [μs],Regular approximation time [μs],Automaton creation time [μs],DFA creation time [μs],Regex creation time [μs],Total time [μs],Original grammar nonterminals,Original grammar productions,Approximated grammar nonterminals,Approximated grammar productions,Automaton nodes,Automaton edges,DFA nodes,DFA edges,Regex size,Regex\n"
    }
}

data class TestData(
    val grammarCreationDuration: Duration,
    val grammarSize: Pair<Int, Int>, // (nonterminals, productions)
    val charsetApproxDuration: Duration,
    val regularApproxDuration: Duration,
    val approximatedGrammarSize: Pair<Int, Int>, // (nonterminals, productions)
    val automatonCreationDuration: Duration,
    val automatonSize: Pair<Int, Int>, // (states, edges)
    val dfaCreationDuration: Duration?, // (states, edges)
    val dfaSize: Pair<Int, Int>?,
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

    /**
     * Benchmarks all test cases in the CWE89_SQL_Injection part of the Juliet test suite. Writes
     * the results to a CSV file. Warning: Running all inputs takes a long time, consider splitting
     * it in batches or just taking the first n (~200) inputs.
     */
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
                    )
                }
            return inputs
        }
        // even though we copy the files to a temporary directory, CPG creation still runs into an
        // endless loop/OOM at some point?
        // workaround: split these inputs into batches of 200 and manually run them separately
        val batch = 1
        val inputs =
            listOf("s01", "s02", "s03", "s04")
                .flatMap { getInputs(it) }
                .drop(200 * (batch - 1))
                .take(200)

        println("Got ${inputs.size} test cases totaling ${inputs.sumOf { it.files.size }} files")
        val julietFileWriter = FileOutputStream("juliet_$batch.csv").bufferedWriter()
        julietFileWriter.write(TestResult.csvHeader)
        julietFileWriter.flush()
        performBenchmarks(inputs) { julietFileWriter.writeCsvRow(it) }
        julietFileWriter.close()
    }

    /**
     * Runs benchmarks for the Tricky and StringProperties examples and writes the results to CSV
     * files. Runs each test 100 times to get better results.
     */
    @Test
    fun simpleBenchmarks() {
        val path = Path.of("src", "test", "resources", "string_properties_benchmarks")
        val tricky =
            TestInput(
                name = "Tricky",
                files = listOf("Tricky.java").map { path.resolve(it).toFile() },
                nodeName = "res",
                lineNumber = 24,
            )
        val stringProperties =
            TestInput(
                name = "StringProperties",
                files = listOf("StringProperties.java").map { path.resolve(it).toFile() },
                nodeName = "res",
                lineNumber = 14,
            )
        val databaseSanitization =
            TestInput(
                name = "DatabaseSanitization",
                files = listOf("DatabaseSanitization.java").map { path.resolve(it).toFile() },
                hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                lineNumber = 13,
            )
        val trickyDfa = tricky.copy(name = "${tricky.name}_dfa", createDFA = true)
        val spDfa = stringProperties.copy(name = "${stringProperties.name}_dfa", createDFA = true)
        val databaseDFA =
            databaseSanitization.copy(name = "${databaseSanitization.name}_dfa", createDFA = true)
        // manually run separately for better results, just comment the other ones out
        repeatAndWrite(tricky, 100, "tricky.csv")
        repeatAndWrite(stringProperties, 100, "stringProperties.csv")
        repeatAndWrite(trickyDfa, 100, "tricky_dfa.csv")
        repeatAndWrite(spDfa, 100, "stringProperties_dfa.csv")
        repeatAndWrite(databaseSanitization, 100, "databaseSanitization.csv")
        repeatAndWrite(databaseDFA, 100, "databaseSanitization_dfa.csv")
    }

    private fun repeatAndWrite(input: TestInput, times: Int, fileName: String) {
        val resultFileWriter = FileOutputStream(fileName).bufferedWriter()
        resultFileWriter.write(TestResult.csvHeader)
        resultFileWriter.flush()
        repeat(times) { no ->
            performBenchmarks(
                listOf(input.copy(name = "${input.name} #$no")),
            ) {
                resultFileWriter.writeCsvRow(it)
            }
        }
    }

    /** Analyzes some fixed hotspots in some given inputs and prints the results. */
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
                TestInput(
                    name = "DatabaseSanitization",
                    files = listOf("DatabaseSanitization.java").map { path.resolve(it).toFile() },
                    hotspotType = StringPropertyHotspots.HotspotType.DATABASE,
                    lineNumber = 13,
                )
            )
        performBenchmarks(inputs, resultAcceptor = ::printResult)
    }

    // the acceptor is used because collecting the results in a list takes a lot of memory
    /**
     * Runs the benchmarks for the given inputs and passes the results to the given acceptor. The
     * acceptor is called for each input separately and if no acceptor is given, the results are
     * discarded.
     * @param inputs the inputs to run the benchmarks for
     * @param resultAcceptor the acceptor to call for each result
     */
    private fun performBenchmarks(
        inputs: List<TestInput>,
        resultAcceptor: ((TestResult) -> Unit) = {}
    ) {
        val newPath = Path.of("/tmp", "string_properties_benchmarks")

        for (input in inputs) {
            StringPropertyHotspots.clear()
            if (input.files.isEmpty()) {
                println("Skipping ${input.name} because no files were found")
                resultAcceptor(TestResult(input.name, true, null))
                continue
            }
            // this is needed because CPG creation scans all files in the directory and the large
            // directories lead to errors
            // therefore we copy the relevant files to a new directory, build the CPG there and then
            // delete the files again
            val newFiles =
                input.files.map {
                    val newFile = newPath.resolve(it.name).toFile()
                    return@map it.copyTo(newFile, overwrite = true)
                }
            buildCPG(newFiles, newPath)
            newPath.listDirectoryEntries().forEach { it.deleteExisting() }

            if (input.analyzeAllHotspots) {
                // println("Analyzing all hotspots for ${input.name}")
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
                    resultAcceptor(
                        TestResult(
                            "${input.name}[node ${hotspot.id} at ${hotspot.location}]",
                            false,
                            data
                        )
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
                    println("Could not find node for input $input")
                    resultAcceptor(TestResult(input.name, true, null))
                    continue
                }

                val data = analyzeNode(node, createDFA = input.createDFA)
                resultAcceptor(TestResult(input.name, false, data))
            }
        }
        StringPropertyHotspots.clear()
    }

    /** Builds a CPG for the given files with path as the toplevel directory */
    private fun buildCPG(files: List<File>, path: Path) {
        TranslationManager.builder().build().analyze()
        TestUtils.analyzeAndGetFirstTU(files, path, true) {
            it.registerLanguage<JavaLanguage>()
                .registerPass(IdentifierPass())
                .registerPass(EdgeCachePass())
                .registerPass(StringPropertyPass())
        }
    }

    /**
     * Performs all steps from node to regex, measures the duration of each step and collects it
     * with the sizes of the results
     * @param node the node to analyze
     * @param createDFA whether to create a DFA from the NFA before converting it to a regex
     */
    @OptIn(ExperimentalTime::class)
    fun analyzeNode(node: Expression, createDFA: Boolean = false): TestData {
        // println("Analyzing node ${node.name} at ${node.location}")
        val (grammar, grammarCreationDuration) = measureTimedValue { createGrammar(node) }

        val originalGrammarSize =
            grammar.getAllNonterminals().size to
                grammar.getAllNonterminals().flatMap { it.productions }.size

        val charsetApproxDuration = measureTime { CharSetApproximation(grammar).approximate() }
        val regularApproxDuration = measureTime { RegularApproximation(grammar).approximate() }

        val regularGrammarSize =
            grammar.getAllNonterminals().size to
                grammar.getAllNonterminals().flatMap { it.productions }.size

        var (automaton, automatonCreationDuration) =
            measureTimedValue { GrammarToNFA(grammar).makeFA() }

        val automatonSize =
            automaton.states.size to automaton.states.flatMap { it.outgoingEdges }.size

        var dfaCreationDuration: Duration? = null
        var dfaSize: Pair<Int, Int>? = null
        if (createDFA) {
            val (dfa, dur) = measureTimedValue { automaton.toDfa() }
            dfaCreationDuration = dur
            automaton = NFA(dfa.states)
            dfaSize = dfa.states.size to dfa.states.flatMap { it.outgoingEdges }.size
        }

        val (pattern, toRegexDuration) = measureTimedValue { automaton.toRegex() }
        return TestData(
            grammarCreationDuration = grammarCreationDuration,
            grammarSize = originalGrammarSize,
            charsetApproxDuration = charsetApproxDuration,
            regularApproxDuration = regularApproxDuration,
            approximatedGrammarSize = regularGrammarSize,
            automatonCreationDuration = automatonCreationDuration,
            automatonSize = automatonSize,
            dfaCreationDuration = dfaCreationDuration,
            dfaSize = dfaSize,
            toRegexDuration = toRegexDuration,
            regexSize = pattern.length,
            regex = pattern,
        )
    }

    private val ansiReset = "\u001B[0m"
    private val ansiRed = "\u001B[31m"
    private val ansiGreen = "\u001B[32m"

    private fun formatDuration(duration: Duration?, total: Duration) =
        duration?.let { "$duration (${String.format("%.2f", duration / total * 100)}% of total)" }
            ?: "N/A"

    private fun printResult(result: TestResult) {
        if (result.error || result.result == null) {
            println("+++++++++++++++++++++++++++++")
            println("${ansiRed}Error in test ${result.name}$ansiReset")
            return
        }
        val data = result.result
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
            DFA creation:              ${formatDuration(data.dfaCreationDuration, data.totalDuration)}
            DFA size:                  ${data.dfaSize?.first ?: "N/A"} states, ${data.dfaSize?.second ?: "N/A"} edges
            Regex creation:            ${formatDuration(data.toRegexDuration, data.totalDuration)}
            Regex size:                ${data.regexSize}
            Total time:                ${data.totalDuration}
            Regex:                     ${prettyPrintPattern(data.regex)}
            """.trimIndent()
        )
    }

    private fun BufferedWriter.writeCsvRow(result: TestResult) {
        this.write(result.toCsvRow())
        this.flush()
    }
}
