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

import java.util.*

class CharSetApproximation(val grammar: ContextFreeGrammar) {
    val charsets: MutableMap<Nonterminal, CharSet> = mutableMapOf()
    val predecessors: MutableMap<Nonterminal, MutableSet<Nonterminal>>
    val scc: SCC

    init {
        predecessors = grammar.getPredecessors()
        scc = SCC(grammar)
        // here we use the property that tarjans algorithm for SCC provides topological ordering
        for (comp in scc.components) {
            findCharSets(comp)
        }
    }

    /**
     * Finds charsets for all nonterminals in the given component, assuming that its successors have
     * been processed.
     */
    fun findCharSets(component: Component) {
        // TODO maybe reset charsets for all nonterminals in component?
        // fixpoint iteration, within this component
        val worklist: SortedSet<Nonterminal> = TreeSet(component.nonterminal)
        while (!worklist.isEmpty()) {
            val n = worklist.first()
            worklist.remove(n)
            if (updateCharset(n, charsets)) {
                for (m in predecessors.getOrDefault(n, emptyList())) {
                    if (scc.getComponentForNonterminal(m) === component) {
                        worklist.add(m)
                    }
                }
            }
        }
    }

    /** Updates charset according to productions. Returns true if any changes. */
    fun updateCharset(nt: Nonterminal, charsets: MutableMap<Nonterminal, CharSet>): Boolean {
        val currentCharSet = charsets.getOrDefault(nt, CharSet.empty())

        val newSets = nt.productions.map { getCharsetForProduction(it) }
        val newSet = currentCharSet.union(newSets)

        charsets[nt] = newSet
        return newSet == currentCharSet
    }

    fun getCharsetForProduction(prod: Production): CharSet {
        return when (prod) {
            is TerminalProduction -> prod.terminal.charset
            is UnitProduction -> {
                val nonterminal = grammar.getNonterminal(prod.y_id)!!
                charsets.getOrDefault(nonterminal, CharSet.empty())
            }
            is UnaryOpProduction -> {
                val nonterminal = grammar.getNonterminal(prod.y_id)!!
                prod.op.charsetTransformation(charsets.getOrDefault(nonterminal, CharSet.empty()))
            }
            is BinaryOpProduction -> {
                val nonterminal1 = grammar.getNonterminal(prod.y_id)!!
                val nonterminal2 = grammar.getNonterminal(prod.z_id)!!
                prod.op.charsetTransformation(
                    charsets.getOrDefault(nonterminal1, CharSet.empty()),
                    charsets.getOrDefault(nonterminal2, CharSet.empty()),
                )
            }
            is ConcatProduction -> {
                val nonterminal1 = grammar.getNonterminal(prod.y_id)!!
                val nonterminal2 = grammar.getNonterminal(prod.z_id)!!
                charsets
                    .getOrDefault(nonterminal1, CharSet.empty())
                    .union(charsets.getOrDefault(nonterminal2, CharSet.empty()))
            }
            else -> throw IllegalStateException("unreachable when branch")
        }
    }
}
