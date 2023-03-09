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
import de.fraunhofer.aisec.cpg.helper.operations.*
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

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

fun prettyPrintPattern(pattern: String): String {
    return pattern.replace("\\Q", "").replace("\\E", "")
}

/** Builds a CPG for the given files with path as the toplevel directory */
fun buildCPG(files: List<File>, path: Path) {
    TranslationManager.builder().build().analyze()
    TestUtils.analyzeAndGetFirstTU(files, path, true) {
        it.registerLanguage<JavaLanguage>()
            .registerPass(IdentifierPass())
            .registerPass(EdgeCachePass())
            .registerPass(StringPropertyPass())
    }
}

/**
 * Performs all steps from node to regex, measures the duration of each step and collects it with
 * the sizes of the results
 * @param node the node to analyze
 * @param createDFA whether to create a DFA from the NFA before converting it to a regex
 */
@OptIn(ExperimentalTime::class)
fun analyzeNode(
    node: Expression,
    createDFA: Boolean = false,
    printResults: Boolean = false
): TestData {
    // println("Analyzing node ${node.name} at ${node.location}")
    val (grammar, grammarCreationDuration) = measureTimedValue { createGrammar(node) }
    if (printResults) println("Initial grammar ${grammar.printGrammar()}")

    val originalGrammarSize =
        grammar.getAllNonterminals().size to
            grammar.getAllNonterminals().flatMap { it.productions }.size

    val charsetApproxDuration = measureTime { CharSetApproximation(grammar).approximate() }
    val regularApproxDuration = measureTime { RegularApproximation(grammar).approximate() }
    if (printResults) println("Approximated grammar ${grammar.printGrammar()}")

    val regularGrammarSize =
        grammar.getAllNonterminals().size to
            grammar.getAllNonterminals().flatMap { it.productions }.size

    var (automaton, automatonCreationDuration) =
        measureTimedValue { GrammarToNFA(grammar).makeFA() }
    if (printResults) println("NFA ${automaton.toDotString()}")

    val automatonSize = automaton.states.size to automaton.states.flatMap { it.outgoingEdges }.size

    var dfaCreationDuration: Duration? = null
    var dfaSize: Pair<Int, Int>? = null
    if (createDFA) {
        val (dfa, dur) = measureTimedValue { automaton.toDfa() }
        dfaCreationDuration = dur
        automaton = NFA(dfa.states)
        if (printResults) println("DFA ${automaton.toDotString()}")

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

fun printResult(result: TestResult) {
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

fun BufferedWriter.writeCsvRow(result: TestResult) {
    this.write(result.toCsvRow())
    this.flush()
}

/**
 * A simple grammar parser for easy grammar creation in tests. The grammar is defined as a string,
 * where each line represents a production. Nonterminals are single uppercase letters, everything
 * else is considered a terminal. For each terminal "a" a new nonterminal "TA" with a single
 * TerminalProduction is created.
 *
 * Format of the grammar:
 * - {single NT} "->" {single symbol} ("|" {single symbol} {single symbol})
 * - Example:
 * ```
 *      - S -> Ab
 *      - A -> Sa | a
 * ```
 * The following operation productions are supported.
 * - `A -> replace[a,b](B)`
 * - `A -> reverse(B)`
 * - `A -> trim(B)`
 * - `A -> toUpperCase(B)`
 * - `A -> toLowerCase(B)`
 */
fun grammarStringToGrammar(grammarString: String): Grammar {
    val operationRegex = Regex("(?<op>[^\\[(]+)(?:\\[(?<opArgs>[^]]+)])?\\((?<arg>[^)]+)\\)")

    val grammar = Grammar()
    val lines = grammarString.split("\n")
    var currentId: Long = 0
    val nonterminals = mutableMapOf<String, Nonterminal>()

    fun handleOperationProduction(prod: String, left: Nonterminal) {
        operationRegex.matchEntire(prod.trim())?.let { result ->
            val op = result.groups["op"]?.value
            val arg = result.groups["arg"]?.value
            val opArgs = result.groups["opArgs"]?.value
            if ((op == null) || (arg == null)) {
                throw IllegalArgumentException("Invalid operation production: $prod")
            }
            val argNT = nonterminals.computeIfAbsent(arg) { Nonterminal(currentId++, label = arg) }
            val operation =
                when (op.lowercase()) {
                    "reverse" -> Reverse()
                    "replace" -> {
                        val args = opArgs?.split(",")?.map { it.trim() }
                        when (args?.size) {
                            2 -> ReplaceBothKnown(args[0][0], args[1][0])
                            else ->
                                throw IllegalArgumentException("Invalid replace arguments: $prod")
                        }
                    }
                    "trim" -> Trim()
                    "touppercase" -> ToUpperCase()
                    "tolowercase" -> ToLowerCase()
                    else -> throw IllegalArgumentException("Unknown operation: $prod")
                }
            left.addProduction(OperationProduction(operation, argNT))
        }
    }

    lines
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            val parts = line.split("->")
            val leftLabel = parts[0].trim()
            val left =
                nonterminals.computeIfAbsent(leftLabel) {
                    Nonterminal(currentId++, label = leftLabel)
                }

            val productions = parts[1].split("|")
            productions.forEach { prod ->
                if (prod.contains("(") && prod.contains(")")) {
                    handleOperationProduction(prod, left)
                } else {
                    val nts =
                        prod
                            .toCharArray()
                            .filter { !it.isWhitespace() }
                            .map { c ->
                                if (!c.isUpperCase()) {
                                    // If the TC nonterminal already exists, return it
                                    nonterminals["T${c.uppercase()}"]?.let {
                                        return@map it
                                    }
                                    // Terminal
                                    val terminalNT =
                                        nonterminals.computeIfAbsent("T${c.uppercase()}") {
                                            Nonterminal(currentId++, label = it)
                                        }
                                    terminalNT.addProduction(
                                        TerminalProduction(Terminal(c.toString()))
                                    )
                                    return@map terminalNT
                                } else {
                                    return@map nonterminals.computeIfAbsent(c.toString()) {
                                        Nonterminal(currentId++, label = it)
                                    }
                                }
                            }
                    when (nts.size) {
                        1 -> left.addProduction(UnitProduction(nts.first()))
                        2 -> left.addProduction(ConcatProduction(nts[0], nts[1]))
                        else -> throw IllegalArgumentException("Invalid production: $prod")
                    }
                }
            }
        }
    grammar.startNonterminal = nonterminals.values.first()
    nonterminals.values.forEach { grammar.addNonterminal(it) }
    return grammar
}
