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
        val A = Nonterminal(0)
        val B = Nonterminal(1)
        val TA = Nonterminal(2)
        val TB = Nonterminal(3)
        val R = Nonterminal(4)

        TA.addProduction(TerminalProduction(Terminal("a")))
        TB.addProduction(TerminalProduction(Terminal("b")))
        R.addProduction(ConcatProduction(TA, B))
        A.addProduction(ConcatProduction(R, TA))
        B.addProduction(ConcatProduction(TB, A))
        B.addProduction(UnitProduction(TB))

        listOf(A, B, TA, TB, R).forEach { g.addNonterminal(it) }

        RegularApproximation(g).approximate()
        println(g.printGrammar())
        // A -> R
        // B -> TB A
        // B -> TB 5
        // TA -> "a"
        // TB -> "b"
        // R -> TA B
        // 5 -> 6
        // 6 -> TA 7
        // 7 -> 5
    }

    @Test
    fun test2() {
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
