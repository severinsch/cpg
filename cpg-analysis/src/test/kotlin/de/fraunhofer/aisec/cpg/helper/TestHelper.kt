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

/**
 * A simple grammar parser for easy grammar creation in tests. The grammar is defined as a string,
 * where each line represents a production. Nonterminals are single uppercase letters, everything
 * else is considered a terminal. For each terminal "a" a new nonterminal "TA" with a single
 * Terminalproduction is created. Operation productions are not supported. Format of the grammar:
 * {single NT} "->" {single symbol} ("|" {single symbol} {single symbol})* Example: S -> Ab A -> Sa
 * | a
 */
fun grammarStringToGrammar(grammarString: String): Grammar {
    val grammar = Grammar()
    val lines = grammarString.split("\n")
    var currentId: Long = 0
    val nonterminals = mutableMapOf<String, Nonterminal>()
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
                val nts =
                    prod
                        .toCharArray()
                        .filter { !it.isWhitespace() }
                        .map { c ->
                            if (!c.isUpperCase()) {
                                // Terminal
                                val terminalNT =
                                    nonterminals.computeIfAbsent("T${c.uppercase()}") {
                                        Nonterminal(currentId++, label = it)
                                    }
                                terminalNT.addProduction(TerminalProduction(Terminal(c.toString())))
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
    grammar.startNonterminal = nonterminals.values.first()
    nonterminals.values.forEach { grammar.addNonterminal(it) }
    return grammar
}
