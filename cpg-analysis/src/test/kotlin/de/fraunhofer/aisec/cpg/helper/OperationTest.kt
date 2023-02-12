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

import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import org.junit.jupiter.api.Test

class OperationTest {

    @Test
    fun exampleOperationProduction() {
        val grammarDefinition =
            """
            A -> E
            A -> replace[f,x](F)
            E -> eE
            E -> e
            F -> fF
            F -> f
        """.trimIndent()
        val g = grammarStringToGrammar(grammarDefinition)
        println("Initial grammar:\n${g.printGrammar()}")

        val nfa = GrammarToNFA(g).makeFA()
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val nfaNoOperations = GrammarToNFA(g).makeFA(false)
        println(
            "NFA without resolved operations:\n${nfaNoOperations.toDotString().replace("\\Q", "").replace("\\E", "")}"
        )

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }

    @Test
    fun testTaintPropagation() {
        val grammarDefinition =
            """
        A -> trim(C)
        C -> toUpperCase(D)
        D -> d
        C -> reverse(E)
        E -> K
        E -> replace[f,x](K)
        K -> fF
        F -> f
        F -> K
        """.trimIndent()
        val g = grammarStringToGrammar(grammarDefinition)
        println("Initial grammar: ${g.printGrammar()}")

        CharSetApproximation(g).approximate()
        println("After CharSet Approximation:\n${g.printGrammar()}")

        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")

        val nfa = GrammarToNFA(g).makeFA(applyOperations = true)
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }

    @Test
    fun testReverse() {
        val grammarDefinition =
            """
            S -> X
            X -> xS
            X -> A
            A -> trim(C)
            C -> toUpperCase(D)
            D -> d
            C -> reverse(E)
            E -> K
            E -> replace[f,x](K)
            K -> fF
            F -> a
            F -> K
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)

        println("Initial grammar:\n${g.printGrammar()}")

        CharSetApproximation(g).approximate()
        println("After CharSet Approximation:\n${g.printGrammar()}")

        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")

        val nfa = GrammarToNFA(g).makeFA(applyOperations = true)
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }

    @Test
    fun testReverse2() {

        val grammarDefinition =
            """
            A -> E
            A -> reverse(F)
            E -> eE
            E -> e
            F -> fF
            F -> f
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)

        println("Initial grammar: ${g.printGrammar()}")

        CharSetApproximation(g).approximate()
        RegularApproximation(g, setOf(0)).approximate()

        val nfaNoOps = GrammarToNFA(g).makeFA(applyOperations = false)
        println("NFA (no ops):\n${nfaNoOps.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val nfa = GrammarToNFA(g).makeFA(applyOperations = true)
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }
}
