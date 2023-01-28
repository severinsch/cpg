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

import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import org.junit.jupiter.api.Test

class RegularApproximationTest {

    @Test
    fun test1() {
        // A -> aBa
        // B -> bA | b

        // represented as
        // TA -> a
        // TB -> b
        // R -> TA B
        // A -> R TA
        // B -> TB A | TB

        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val B = Nonterminal(1, label = "B")
        val TA = Nonterminal(2, label = "TA")
        val TB = Nonterminal(3, label = "TB")
        val R = Nonterminal(4, label = "R")

        TA.addProduction(TerminalProduction(Terminal("a")))
        TB.addProduction(TerminalProduction(Terminal("b")))
        R.addProduction(ConcatProduction(TA, B))
        A.addProduction(ConcatProduction(R, TA))
        B.addProduction(ConcatProduction(TB, A))
        B.addProduction(UnitProduction(TB))

        listOf(A, B, TA, TB, R).forEach { g.addNonterminal(it) }
        g.startNonterminal = A
        println("Initial grammar: ${g.printGrammar()}")
        val scc =
            SCC(g).also {
                it.components.forEach { c ->
                    c.determineRecursion()
                    println("Comp: ${c.nonterminals}: ${c.recursion}")
                }
            }
        println("Grammar Graph before Approximation:\n${g.toDOT(scc)}")

        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")
        // A -> R
        // B -> TB A
        // B -> TB B'
        // TA -> "a"
        // TB -> "b"
        // R -> TA B
        // B' -> R'
        // R' -> TA A'
        // A' -> "ε"
        // A' -> B'
        val sccApprox =
            SCC(g).also {
                it.components.forEach { c ->
                    c.determineRecursion()
                    println("Comp: ${c.nonterminals}: ${c.recursion}")
                }
            }
        println("Grammar Graph:\n${g.toDOT(sccApprox)}")
        val nfa = GrammarToNFA(g).makeFA()
        println("NFA:\n${nfa.toDotString()}")

        val pattern = nfa.toRegex()
        println("Pattern: $pattern")
    }

    @Test
    fun test2() {
        // A -> C
        // C -> bD
        // C -> aA
        // D -> d

        // abd
        // aabd
        // bd
        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val C = Nonterminal(1, label = "C")
        val D = Nonterminal(2, label = "D")
        val TB = Nonterminal(3, label = "TB")
        val TA = Nonterminal(4, label = "TA")
        val TD = Nonterminal(5, label = "TD")
        A.addProduction(UnitProduction(C))
        C.addProduction(ConcatProduction(TA, A))
        C.addProduction(ConcatProduction(TB, D))
        D.addProduction(UnitProduction(TD))
        TB.addProduction(TerminalProduction(Terminal("b")))
        TA.addProduction(TerminalProduction(Terminal("a")))
        TD.addProduction(TerminalProduction(Terminal("d")))
        listOf(A, C, D, TB, TA, TD).forEach { g.addNonterminal(it) }
        g.startNonterminal = A
        println("Initial grammar: ${g.printGrammar()}")
        SCC(g).also {
            it.components.forEach { c ->
                c.determineRecursion()
                println("Comp: ${c.nonterminals}: ${c.recursion}")
            }
        }
        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")

        val nfa = GrammarToNFA(g).makeFA()
        println("NFA:\n${nfa.toDotString()}")

        val pattern = nfa.toRegex()
        println("Pattern: $pattern")
    }

    @Test
    fun test3() {
        // S -> T S | a
        // T -> S +
        val g = Grammar()
        val S = Nonterminal(0)
        val T = Nonterminal(1)
        val P = Nonterminal(2)
        S.addProduction(ConcatProduction(T, S))
        S.addProduction(TerminalProduction(Terminal("a")))
        T.addProduction(ConcatProduction(S, P))
        P.addProduction(TerminalProduction(Terminal("+")))

        listOf(S, T, P).forEach { g.addNonterminal(it) }

        RegularApproximation(g, setOf(0)).approximate()
        println(g.printGrammar())

        // S -> T
        // S -> R S'
        // T -> S
        // P -> "+"
        // T' -> S
        // S' -> ""
        // S' -> P T'
        // S' -> S'
        // R -> "a"
        // ==> S -> S | a ("" | + S) =?= a(+a)∗, was laut paper richtig ist?

    }
}
