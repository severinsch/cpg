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

    private fun addEdge(from: State, to: State, op: String) {
        val newOp = op.ifEmpty { "ε" }

        automaton.addEdge(from, Edge(newOp, nextState = to))
    }

    private fun addEdge(from: State, to: State, t: Terminal) {
        val edgeVal =
            when {
                t.isEpsilon -> ""
                t.isLiteral -> Regex.escape(t.value)
                else -> t.value
            }
        addEdge(from, to, edgeVal)
    }

    private fun nederhofMakeFA(state0: State, nts: List<Symbol>, state1: State) {
        if (nts.isEmpty()) {
            addEdge(state0, state1, "")
            return
        }
        // if α = a, some a in Σ
        if (nts.size == 1 && nts.first() is Terminal) {
            addEdge(state0, state1, nts.first() as Terminal)
            return
        }
        // test
        if (
            nts.size == 1 &&
                (nts.first() as Nonterminal).productions.size == 1 &&
                (nts.first() as Nonterminal).productions.first() is TerminalProduction
        ) {
            addEdge(
                state0,
                state1,
                ((nts.first() as Nonterminal).productions.first() as TerminalProduction).terminal
            )
            return
        }

        // if α = Xβ, some X in V, β in V* such that |β| > 0
        if (nts.size > 1) {
            val q = automaton.addState()
            val X = nts.first()
            val beta = nts.drop(1)
            nederhofMakeFA(state0, listOf(X), q)
            nederhofMakeFA(q, beta, state1)
            return
        }
        // α = single Nonterminal
        // TODO different definiton of recursive Nonterminals, maybe change SCC to exclude
        // components with only one element (if element has no loop to itself?)
        val A = nts.first() as Nonterminal
        val comp = scc.getComponentForNonterminal(A)
        if (
            comp != null &&
                (comp.nonterminals.size != 1 ||
                    // loop to itself counted as existing Ni in Nederhof paper
                    A.productions.any { prod -> prod.getAllTargetSymbols().contains(A) })
        ) { // α must consist of a single nonterminal

            // for each B e Ni do let qB = fresh_state end; in map to access for each NT
            val ntStates = comp.nonterminals.associateWith { automaton.addState() }

            if (comp.recursion == Recursion.LEFT) {
                for (C in comp.nonterminals) {
                    for (prod in C.productions) {
                        val allSymbols = prod.getAllTargetSymbols()
                        //  ( C --> X1 ... Xm) in P such that C in Ni && X1, ..., Xm not in Ni
                        if (
                            allSymbols.all { X ->
                                X is Terminal || (X is Nonterminal && !comp.contains(X))
                            }
                        ) {
                            nederhofMakeFA(state0, allSymbols, ntStates[C]!!)
                        }

                        val D = allSymbols.first()
                        val rest = allSymbols.drop(1)
                        //  ( C --> DX1 ... Xm) in P such that C, D in Ni && X1, ..., Xm not in Ni
                        if (
                            D is Nonterminal &&
                                comp.contains(D) &&
                                rest.all { X ->
                                    X is Terminal || (X is Nonterminal && !comp.contains(X))
                                }
                        ) {
                            nederhofMakeFA(ntStates[D]!!, rest, ntStates[C]!!)
                        }
                    }
                    // let Δ = Δ U {(qA, ε, q1)}
                    addEdge(ntStates[A]!!, state1, "")
                }
            } else { // recursion type RIGHT
                // "the converse of the then part"???
                for (C in comp.nonterminals) {
                    for (prod in C.productions) {
                        val allSymbols = prod.getAllTargetSymbols()
                        //  ( C --> X1 ... Xm) in P such that C in Ni && X1, ..., Xm not in Ni ??
                        if (
                            allSymbols.all { X ->
                                X is Terminal || (X is Nonterminal && !comp.contains(X))
                            }
                        ) {
                            nederhofMakeFA(state0, allSymbols, ntStates[C]!!)
                        }

                        val D = allSymbols.last()
                        val rest = allSymbols.dropLast(1)
                        //  ( C --> X1 ... XmD) in P such that C in Ni && X1, ..., Xm not in Ni
                        if (
                            D is Nonterminal &&
                                comp.contains(D) &&
                                rest.all { X ->
                                    X is Terminal || (X is Nonterminal && !comp.contains(X))
                                }
                        ) {
                            nederhofMakeFA(ntStates[D]!!, rest, ntStates[C]!!)
                        }
                    }
                    // let Δ = Δ U {(qA, ε, q1)}
                    addEdge(ntStates[A]!!, state1, "")
                }
            }
        } else { // A is not recursive
            println("A not recursive: A = ${A.id}")
            for (prod in A.productions) {
                nederhofMakeFA(state0, prod.getAllTargetSymbols(), state1)
            }
        }
    }
}
