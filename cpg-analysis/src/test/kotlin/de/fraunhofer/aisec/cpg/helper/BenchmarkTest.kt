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
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import org.junit.jupiter.api.*

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
}
