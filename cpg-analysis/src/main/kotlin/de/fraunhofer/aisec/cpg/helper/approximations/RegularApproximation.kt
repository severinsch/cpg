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

class RegularApproximation(private val grammar: ContextFreeGrammar) {
    private lateinit var scc: SCC
    private val needEpsilonProduction: MutableSet<Nonterminal> = mutableSetOf()
    private val newProductions: MutableMap<Nonterminal, MutableSet<Production>> = mutableMapOf()
    // mapping from NT A to newly created NT A'
    private val primedNonterminals: MutableMap<Nonterminal, Nonterminal> = mutableMapOf()

    fun approximate() {
        scc = SCC(grammar)
        scc.components.forEach { it.determineRecursion() }


        for (nt in grammar.getAllNonterminals()) {
            //if (hotspots.contains(nt)) {
            //    needEpsilonProduction[nt] = true
            //}
            for (succ in grammar.getSuccessorsFor(nt)) {
                if (scc.getComponentForNonterminal(nt) != scc.getComponentForNonterminal(succ)) {
                   needEpsilonProduction.add(succ)
                }
            }
        }


        for (comp in scc.components) {
            if(comp.recursion != Recursion.BOTH) {
                continue
            }

            // created A' for A
            for (a in comp.nonterminals) {
                val aPrimed = grammar.createNewNonterminal().also{comp.nonterminals.add(it)}

                primedNonterminals[a] = aPrimed
                if(needEpsilonProduction.contains(a)) {
                    // TODO use extra epsilon production?
                    aPrimed.addProduction(TerminalProduction(Terminal("")))
                }
            }

            // make new productions
            for (a in comp.nonterminals) {
                for (prod in a.productions) {
                    handleProduction(comp, a, prod)
                }
            }
            comp.recursion = Recursion.RIGHT
        }

        // replace old productions with newly created ones for affected nodes
        for ((nt, newProds) in newProductions) {
            nt.replaceProductions(newProds)
        }
    }

    private fun addProduction(from: Nonterminal, prod: Production) {
        newProductions.computeIfAbsent(from) { mutableSetOf() }.add(prod)
    }


    private fun handleProduction(comp: Component, nt: Nonterminal, prod: Production) {
        when (prod) {
            // A -> B
            is UnitProduction -> {
                if (comp.contains(prod.target1)){
                    // A -> B  =>  A -> B, B' -> A'
                    addProduction(from = nt, UnitProduction(prod.target1))
                    addProduction(from = primedNonterminals[prod.target1]!!, UnitProduction(primedNonterminals[nt]!!))
                } else {
                    // A -> X  =>  A -> X A'
                    addProduction(from = nt, ConcatProduction(prod.target1, primedNonterminals[nt]!!))
                }
            }
            is ConcatProduction -> {
                when (Pair(comp.contains(prod.target1), comp.contains(prod.target2))) {
                    Pair(true, true) -> {
                        // A -> B C  =>  A -> B, B' -> C, C' -> A'
                        addProduction(from = nt, UnitProduction(prod.target1))
                        addProduction(from = primedNonterminals[prod.target1]!!, UnitProduction(prod.target2))
                        addProduction(from = primedNonterminals[prod.target2]!!, UnitProduction(primedNonterminals[nt]!!))

                    }
                    Pair(true, false) -> {
                        // A -> B X  =>  A -> B, B' -> X A'
                        addProduction(from = nt, UnitProduction(prod.target1))
                        addProduction(from = primedNonterminals[prod.target1]!!, ConcatProduction(prod.target2, primedNonterminals[nt]!!))
                    }
                    Pair(false, true) -> {
                        // A -> X B  =>  A -> X B, B' -> A'
                        addProduction(from = nt, ConcatProduction(prod.target1, prod.target2))
                        addProduction(from = primedNonterminals[prod.target2]!!, UnitProduction(primedNonterminals[nt]!!))
                    }
                    Pair(false, false) -> {
                        // A -> X Y  =>  A -> R A', R -> X Y
                        val newNT = grammar.createNewNonterminal().also{comp.nonterminals.add(it)}
                        addProduction(from = nt, ConcatProduction(newNT, primedNonterminals[nt]!!))
                        addProduction(from = newNT, ConcatProduction(prod.target1, prod.target2))
                    }
                }
            }
            is UnaryOpProduction -> {
                // A -> op(X)  =>  A -> R A', R -> op(X)
                val newNT = grammar.createNewNonterminal().also{comp.nonterminals.add(it)}
                addProduction(from = nt, ConcatProduction(newNT, primedNonterminals[nt]!!))
                addProduction(from = nt, UnaryOpProduction(prod.op, prod.target1, prod.other_args))
            }
            is BinaryOpProduction -> {
                // A -> op2(X,Y)  =>  A -> R A', R -> op2(X,Y)
                val newNT = grammar.createNewNonterminal().also{comp.nonterminals.add(it)}
                addProduction(from = nt, ConcatProduction(newNT, primedNonterminals[nt]!!))
                addProduction(from = nt, BinaryOpProduction(prod.op, prod.target1, prod.target2, prod.other_args))
            }
            is TerminalProduction -> {
                // A -> reg  =>  A -> R A', R -> reg
                val newNT = grammar.createNewNonterminal().also{comp.nonterminals.add(it)}
                addProduction(from = nt, ConcatProduction(newNT, primedNonterminals[nt]!!))
                addProduction(from = newNT, TerminalProduction(prod.terminal))
            }
        }
    }
}
