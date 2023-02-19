/*
 * Copyright (c) 2022, Fraunhofer AISEC. All rights reserved.
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

import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import kotlin.math.max

class Grammar(private val nonterminals: HashMap<Long, Nonterminal> = hashMapOf()) {
    private var maxId = nonterminals.keys.maxOrNull() ?: -1
    var startNonterminal: Nonterminal? = null

    fun clear() {
        nonterminals.clear()
    }

    fun approximateToRegularGrammar(hotspotIds: Set<Long> = emptySet()) {
        CharSetApproximation(this).approximate()
        RegularApproximation(this, hotspotIds).approximate()
    }

    fun getAllNonterminals(): MutableCollection<Nonterminal> {
        return nonterminals.values
    }

    /**
     * Creates a new [Nonterminal] with an unused id and adds it to the grammar.
     * @return the newly created [Nonterminal]
     */
    fun createNewNonterminal(): Nonterminal {
        maxId++
        return Nonterminal(maxId).also { nonterminals[maxId] = it }
    }

    /**
     * Adds the given [Nonterminal] to the grammar if there is no existing [Nonterminal] with the
     * same id.
     */
    fun addNonterminal(nt: Nonterminal) {
        maxId = max(maxId, nt.id)
        nonterminals.putIfAbsent(nt.id, nt)
    }

    /**
     * Returns the [Nonterminal] associated with the passed [id] if it exists. If it doesn't exist,
     * a new [Nonterminal] with the given [id] is added to the grammar and returned.
     * @param id Assumes id is not null to limit the amount of non-null assertions when calling the
     * function
     * @return the existing [Nonterminal] or the newly created [Nonterminal]
     */
    fun getOrCreateNonterminal(id: Long?): Nonterminal {
        maxId = max(maxId, id!!)
        // if id is not present, adds new Nonterminal and returns it
        // if id is present returns value
        return nonterminals.computeIfAbsent(id) { k -> Nonterminal(k) }
    }

    /**
     * Finds the successors for the given [Nonterminal].
     * @return an [Iterable] containg all successors of [nt]
     */
    fun getSuccessorsFor(nt: Nonterminal): Iterable<Nonterminal> {
        return nt.productions.flatMap { p ->
            when (p) {
                is TerminalProduction -> emptyList()
                is UnaryProduction -> listOf(p.target1)
                is BinaryProduction -> listOf(p.target1, p.target2)
            }
        }
    }

    /**
     * Finds the predecessors for each [Nonterminal] in the grammar.
     * @return a mapping from each [Nonterminal] to its predecessors
     */
    fun getAllPredecessors(): Map<Nonterminal, Set<Nonterminal>> {
        val predecessors = mutableMapOf<Nonterminal, MutableSet<Nonterminal>>()
        for (nt in nonterminals.values) {
            for (prod in nt.productions) {
                when (prod) {
                    is UnaryProduction -> {
                        // for nt -> B, nt -> op(B) adds nt to predecessors[B]
                        predecessors.computeIfAbsent(prod.target1) { mutableSetOf() }.add(nt)
                    }
                    is BinaryProduction -> {
                        // for nt -> X Y, nt -> op(X, Y) adds nt to predecessors[X] and
                        // predecessors[Y]
                        for (x in listOfNotNull(prod.target1, prod.target2)) {
                            predecessors.computeIfAbsent(x) { mutableSetOf() }.add(nt)
                        }
                    }
                    is TerminalProduction -> {}
                }
            }
        }
        return predecessors
    }

    fun printGrammar(): String {
        return nonterminals.entries.joinToString(separator = "\n") { (_, nt) ->
            nt.productions.joinToString(separator = "\n") { p ->
                "${nt.label} -> " +
                    when (p) {
                        is TerminalProduction -> "\"${p.terminal.value}\""
                        is UnitProduction -> p.target1.label
                        is OperationProduction -> "${p.op}(${p.target1.label})"
                        is ConcatProduction -> "${p.target1.label} ${p.target2.label}"
                    }
            }
        }
    }

    fun toDOT(scc: SCC? = null): String {
        val nodes =
            nonterminals.values.joinToString(separator = "\n", postfix = "\n") { nt ->
                "node_${nt.id} [label = \"${nt.label}\"];"
            }
        val edges =
            nonterminals.values.joinToString(separator = "\n", postfix = "\n") { nt ->
                nt.productions.joinToString(separator = "\n") { prod ->
                    when (prod) {
                        is BinaryProduction ->
                            "node_${nt.id} -> node_${prod.target1.id};\n" +
                                "node_${nt.id} -> node_${prod.target2.id};"
                        is TerminalProduction ->
                            "node_${nt.id} -> \"regex${prod.hashCode()}_${prod.terminal.value}\";"
                        is UnaryProduction -> "node_${nt.id} -> node_${prod.target1.id};"
                    }
                }
            }
        val sccSubgraphs =
            scc?.components?.joinToString(separator = "\n") { comp ->
                "subgraph cluster_${comp.hashCode()}{${
            comp.nonterminals.joinToString(separator = "; ") {
                "node_${it.id}"
            }
        }}"
            }
                ?: ""

        return "digraph grammar {\n$nodes$edges$sccSubgraphs\n}"
    }
}
