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
package de.fraunhofer.aisec.cpg.helper.approximations

import de.fraunhofer.aisec.cpg.helper.*
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class CharSetApproximation(private val grammar: Grammar) {
    // lateinit to delay all computations until approximate is called
    private lateinit var charsets: MutableMap<Nonterminal, CharSet>
    private lateinit var predecessors: Map<Nonterminal, Set<Nonterminal>>
    private lateinit var scc: SCC

    fun approximate() {
        charsets = mutableMapOf()
        predecessors = grammar.getAllPredecessors()
        // compute strongly connected components
        scc = SCC(grammar)
        // here we use the property that Tarjan's algorithm for SCCs provides reverse topological
        // ordering of the components.
        // This is important because the fixpoint computation in findCharSets assumes that all
        // successors of the component have been processed.
        // This in turn is important, because the fixpoint computation uses the updateCharsets
        // function, which uses the charsets of the successors of the given Nonterminal.
        for (comp in scc.components) {
            findCharSets(comp)
        }
        breakCycles()
    }

    private fun breakCycles() {
        var done = false

        while (!done) {
            done = true
            for (comp in scc.components) {
                var cycles = 0
                var maxNT: Nonterminal? = null
                var maxProd: OperationProduction? = null
                var maxOp: Operation? = null

                comp.nonterminals.forEach { nt ->
                    nt.productions.forEach { prod ->
                        if (comp.detectOperationCycle(prod)) {
                            if (prod.op.priority > (maxOp?.priority ?: Int.MIN_VALUE)) {
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

    // TODO check this :( JSA vs paper
    /**
     * Replaces the given [OperationProduction] A -> op(B) with the [TerminalProduction] A ->
     * (op.transform(B.charset))*. Assumes that there is a [CharSet] present in [charsets] for each
     * [Nonterminal]
     */
    private fun replaceOperationProduction(prod: OperationProduction, nt: Nonterminal) {
        val charset: CharSet =
            when (prod) {
                is UnaryOpProduction -> {
                    val oldCharset = charsets[prod.target1]
                    // TODO improve this
                    prod.op.charsetTransformation(oldCharset!!)
                }
                is BinaryOpProduction -> {
                    val oldCharset1 = charsets[prod.target1]
                    val oldCharset2 = charsets[prod.target2]
                    // TODO improve this
                    prod.op.charsetTransformation(oldCharset1!!, oldCharset2!!)
                }
            }
        nt.productions.remove(prod)
        val terminal = Terminal(Regex(charset.toRegexPattern()), charset)
        nt.productions.add(TerminalProduction(terminal))
    }

    /**
     * Detects whether the given [Production] is an [OperationProduction] in a cycle in the
     * [Component].
     * @return true if [prod] is an [OperationProduction] and part of a cycle, false otherwise
     */
    @OptIn(ExperimentalContracts::class)
    private fun Component.detectOperationCycle(prod: Production): Boolean {
        contract { returns(true) implies (prod is OperationProduction) }
        return when (prod) {
            is UnaryOpProduction -> prod.target1 in this.nonterminals
            is BinaryOpProduction ->
                prod.target1 in this.nonterminals || prod.target2 in this.nonterminals
            else -> false
        }
    }

    /**
     * Finds the [CharSet] for each [Nonterminal] in the given [component], assuming that the
     * components successors have been processed.
     */
    private fun findCharSets(component: Component) {
        // TODO maybe reset charsets for all nonterminals in component?
        // fixpoint iteration, within this component
        val worklist = component.nonterminals.toSortedSet()
        while (!worklist.isEmpty()) {
            val n = worklist.first()
            worklist.remove(n)
            if (updateCharset(n, charsets)) {
                for (m in predecessors.getOrDefault(n, emptySet())) {
                    if (scc.getComponentForNonterminal(m) === component) {
                        worklist.add(m)
                    }
                }
            }
        }
    }

    /**
     * Updates the [CharSet] corresponding to [nt] in [charsets] according to the productions of
     * [nt].
     * @return true if [nt]'s [CharSet] changed, false otherwise
     */
    private fun updateCharset(
        nt: Nonterminal,
        charsets: MutableMap<Nonterminal, CharSet>
    ): Boolean {
        val currentCharSet = charsets.getOrDefault(nt, CharSet.empty())

        val newSets = nt.productions.map { getCharsetForProduction(it) }
        val newSet = currentCharSet.union(newSets)

        charsets[nt] = newSet
        return newSet != currentCharSet
    }

    private fun getCharsetForProduction(prod: Production): CharSet {
        return when (prod) {
            is TerminalProduction -> prod.terminal.charset
            is UnitProduction -> {
                charsets.getOrDefault(prod.target1, CharSet.empty())
            }
            is UnaryOpProduction -> {
                prod.op.charsetTransformation(charsets.getOrDefault(prod.target1, CharSet.empty()))
            }
            is BinaryOpProduction -> {
                prod.op.charsetTransformation(
                    charsets.getOrDefault(prod.target1, CharSet.empty()),
                    charsets.getOrDefault(prod.target2, CharSet.empty()),
                )
            }
            is ConcatProduction -> {
                charsets.getOrDefault(prod.target1, CharSet.empty()) union
                    charsets.getOrDefault(prod.target2, CharSet.empty())
            }
        }
    }
}
