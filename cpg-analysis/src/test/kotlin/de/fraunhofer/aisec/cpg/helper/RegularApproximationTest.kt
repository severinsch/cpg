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

        val grammarDefinition =
            """
        A -> Ra
        R -> aB
        B -> bA | b
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)

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
    fun test3() {
        val grammarDefinition = """
        S -> T S | a
        T -> S +
        """.trimIndent()
        val g = grammarStringToGrammar(grammarDefinition)

        RegularApproximation(g).approximate()
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
