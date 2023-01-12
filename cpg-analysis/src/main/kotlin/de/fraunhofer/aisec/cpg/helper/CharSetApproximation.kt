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
    var scc: SCC

    init {
        predecessors = grammar.getPredecessors()
        scc = SCC(grammar)
        // here we use the property that tarjans algorithm for SCC provides topological ordering
        for (comp in scc.components) {
            findCharSets(comp)
        }
        breakCycles()
    }

    private fun breakCycles() {
        var done = false

        while(!done) {
            done = true
            for (comp in scc.components) {
                var cycles = 0
                var maxNT: Nonterminal? = null
                var maxProd: OperationProduction? = null
                var maxOp: Operation? = null

                comp.nonterminal.forEach { nt ->
                    nt.productions.forEach { prod ->
                        if (comp.detectOperationCycle(prod)) {
                            if ((prod as OperationProduction).op.priority > (maxOp?.priority ?: Int.MIN_VALUE)) {
                                maxNT = nt
                                maxProd = prod
                                maxOp = prod.op
                            }
                            cycles++
                        }
                    }
                }

                if (cycles > 0) {
                    done = done && cycles <= 1
                    // replace operation production
                    replaceOperationProduction(maxProd!!, maxNT!!)
                }
            }

            if (!done) {
                // recompute strongly connected components
                scc = SCC(grammar)
            }
        }
    }

    private fun replaceOperationProduction(prod: OperationProduction, nt: Nonterminal) {

        val charset: CharSet = when (prod) {
            is UnaryOpProduction -> {
                val oldCharset = charsets[grammar.getNonterminal(prod.y_id)]
                // TODO improve this
                prod.op.charsetTransformation(oldCharset!!)
            }
            is BinaryOpProduction -> {
                val oldCharset1 = charsets[grammar.getNonterminal(prod.y_id)]
                val oldCharset2 = charsets[grammar.getNonterminal(prod.z_id)]
                // TODO improve this
                prod.op.charsetTransformation(oldCharset1!!, oldCharset2!!)
            }
        }
        nt.productions.remove(prod)
        val terminal = Terminal(Regex(charset.toRegexPattern()), charset)
        nt.productions.add(TerminalProduction(terminal))
    }

    private fun Component.detectOperationCycle(prod: Production): Boolean {
        return when (prod) {
            is UnaryOpProduction -> this.nonterminal.contains(grammar.getNonterminal(prod.y_id))
            is BinaryOpProduction -> this.nonterminal.contains(grammar.getNonterminal(prod.y_id)) ||
                    this.nonterminal.contains(grammar.getNonterminal(prod.z_id))
            else -> false
        }
    }

    /**
     * Finds charsets for all nonterminals in the given component, assuming that its successors have
     * been processed.
     */
    private fun findCharSets(component: Component) {
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
    private fun updateCharset(nt: Nonterminal, charsets: MutableMap<Nonterminal, CharSet>): Boolean {
        val currentCharSet = charsets.getOrDefault(nt, CharSet.empty())

        val newSets = nt.productions.map { getCharsetForProduction(it) }
        val newSet = currentCharSet.union(newSets)

        charsets[nt] = newSet
        return newSet == currentCharSet
    }

    private fun getCharsetForProduction(prod: Production): CharSet {
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
        }
    }
}
