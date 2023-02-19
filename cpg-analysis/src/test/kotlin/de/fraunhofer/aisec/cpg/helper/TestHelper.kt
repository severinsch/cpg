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

import de.fraunhofer.aisec.cpg.helper.operations.*

fun prettyPrintPattern(pattern: String): String {
    return pattern.replace("\\Q", "").replace("\\E", "")
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
                println("Parsing Production $prod")
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
