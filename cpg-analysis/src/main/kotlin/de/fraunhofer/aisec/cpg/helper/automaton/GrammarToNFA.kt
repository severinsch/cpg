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
package de.fraunhofer.aisec.cpg.helper.automaton

import de.fraunhofer.aisec.cpg.analysis.fsm.Edge
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.helper.*

fun Production.getAllTargetSymbols(): List<Symbol> {
    return when (this) {
        is TerminalProduction -> listOf(this.terminal)
        is ConcatProduction -> listOf(this.target1, this.target2)
        is UnitProduction -> listOf(this.target1)
        // TODO figure out how to handle operation productions here
        is UnaryOpProduction -> listOf(this.target1)
        is BinaryOpProduction -> listOf(this.target1, this.target2)
    }
}

class GrammarToNFA(val grammar: Grammar) {
    private val automaton: NFA = NFA()
    private val scc: SCC = SCC(grammar)

    init {
        scc.components.forEach { it.determineRecursion() }
        // Nederhof FA automaton construction has slightly different definition of the sets of
        // mutually recursive nonterminals
        // scc.components.removeIf { it.nonterminals.size == 1 }
    }

    fun makeFA(): NFA {
        if (grammar.startNonterminal == null) {
            throw IllegalStateException("Grammar has no start nonterminal")
        }

        val start = automaton.addState(isStart = true)
        val end = automaton.addState(isAcceptingState = true)
        nederhofMakeFA(start, listOf(grammar.startNonterminal!!), end)
        return automaton
    }

    private fun addEdge(from: State, to: State, t: Terminal) {
        val edgeVal =
            when {
                t.isEpsilon -> "ε"
                t.isLiteral -> Regex.escape(t.value)
                else -> t.value
            }
        automaton.addEdge(from, Edge(edgeVal.ifEmpty { "ε" }, nextState = to))
    }

    // Nederhof FA automaton construction from
    // https://mjn.host.cs.st-andrews.ac.uk/publications/2000d.pdf
    // version for recursion == right not present in paper, but this adaption of the left recursion
    // case seems to work
    private fun nederhofMakeFA(state0: State, alpha: List<Symbol>, state1: State) {
        // if α = ε, maybe can be combined with next case, part after or is not necessary
        if (alpha.isEmpty() || alpha.all { it is Terminal && it.isEpsilon }) {
            addEdge(state0, state1, Terminal.epsilon())
            return
        }
        // if α = a, some a in Σ
        if (alpha.size == 1 && alpha.first() is Terminal) {
            addEdge(state0, state1, alpha.first() as Terminal)
            return
        }
        // if α = Xβ, some X in V, β in V* such that |β| > 0
        if (alpha.size > 1) {
            val q = automaton.addState()
            val X = alpha.first()
            val beta = alpha.drop(1)
            nederhofMakeFA(state0, listOf(X), q)
            nederhofMakeFA(q, beta, state1)
            return
        }
        // α = single Nonterminal
        val A = alpha.first() as Nonterminal
        val comp = scc.getComponentForNonterminal(A)
        if (
            comp != null &&
                (comp.nonterminals.size > 1 ||
                    // loop to itself counted as existing Ni in Nederhof paper
                    A.productions.any { prod -> prod.getAllTargetSymbols().contains(A) })
        ) { // α must consist of a single nonterminal

            // for each B e Ni do let qB = fresh_state end; in map to access for each NT
            val ntStates = comp.nonterminals.associateWith { automaton.addState() }

            for (C in comp.nonterminals) {
                for (prod in C.productions) {
                    val allSymbols = prod.getAllTargetSymbols()
                    if (allSymbols.none { X -> X is Nonterminal && comp.contains(X) }) {
                        //  ( C --> X1 ... Xm) in P such that C in Ni && X1, ..., Xm not in Ni
                        if (comp.recursion == Recursion.LEFT) {
                            nederhofMakeFA(state0, allSymbols, ntStates[C]!!)
                        } else {
                            nederhofMakeFA(ntStates[C]!!, allSymbols, state1)
                        }
                    }

                    val D: Symbol
                    val rest: List<Symbol>
                    if (comp.recursion == Recursion.LEFT) {
                        //  ( C --> DX1 ... Xm) in P such that C, D in Ni && X1, ..., Xm not in Ni
                        D = allSymbols.first()
                        rest = allSymbols.drop(1)
                    } else {
                        //  ( C --> X1 ... XmD) in P such that C, D in Ni && X1, ..., Xm not in Ni
                        D = allSymbols.last()
                        rest = allSymbols.dropLast(1)
                    }

                    if (
                        D is Nonterminal &&
                            comp.contains(D) &&
                            rest.none { X -> X is Nonterminal && comp.contains(X) }
                    ) {
                        if (comp.recursion == Recursion.LEFT) {
                            nederhofMakeFA(ntStates[D]!!, rest, ntStates[C]!!)
                        } else {
                            nederhofMakeFA(ntStates[C]!!, rest, ntStates[D]!!)
                        }
                    }
                }
            }

            if (comp.recursion == Recursion.LEFT) {
                // let Δ = Δ U {(qA, ε, q1)}
                addEdge(ntStates[A]!!, state1, Terminal.epsilon())
            } else {
                // let Δ = Δ U {(q0, ε, qA)}
                addEdge(state0, ntStates[A]!!, Terminal.epsilon())
            }
        } else { // A is not recursive
            for (prod in A.productions) {
                nederhofMakeFA(state0, prod.getAllTargetSymbols(), state1)
            }
        }
    }
}
