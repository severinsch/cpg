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

import de.fraunhofer.aisec.cpg.analysis.fsm.Edge
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class GrammarToAutomatonTest {

    @Test
    fun example1() {
        val grammarDefinition =
            """
        S -> T S
        S -> a
        T -> S P
        P -> +
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)
        CharSetApproximation(g).approximate()
        RegularApproximation(g, setOf(0)).approximate()

        val automaton = GrammarToNFA(g).makeFA()
        val dfa = automaton.toDfa()
        val dfaAsNfa = NFA(dfa.states) // same automaton, just to make typechecking work

        val dfaRegexPattern = dfaAsNfa.toRegex()
        val nfaRegexPattern = automaton.toRegex()

        val nfaRegex = Regex(nfaRegexPattern)
        assert(nfaRegex.matches("a+a+a+a"))
        assert(nfaRegex.matches("a"))
        assertFalse(nfaRegex.matches(""))
        assertFalse(nfaRegex.matches("a+a+"))
        assertFalse(nfaRegex.matches("+a+a"))

        val dfaRegex = Regex(dfaRegexPattern)
        assert(dfaRegex.matches("a+a+a+a"))
        assert(dfaRegex.matches("a"))
        assertFalse(dfaRegex.matches(""))
        assertFalse(dfaRegex.matches("a+a+"))
        assertFalse(dfaRegex.matches("+a+a"))
    }

    @Test
    fun example3() {
        // aus nederhof paper
        val grammarDefinition =
            """
            S -> Aa
            A -> SB
            A -> Bb
            B -> Bc
            B -> d
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)

        RegularApproximation(g, setOf(0)).approximate()
        val automaton = GrammarToNFA(g).makeFA()
        println(automaton.toDotString())
        println(automaton.states.size)
        assert(automaton.states.size == 7)
        assert(automaton.states.flatMap { it.outgoingEdges }.size == 9)

        // use regex to check for correctness of automaton
        val nfaRegexPattern = automaton.toRegex()
        val nfaRegex = Regex(nfaRegexPattern)
        assert(nfaRegex.matches("dba"))
        assert(nfaRegex.matches("dccba"))
        assert(nfaRegex.matches("dcbadccca"))
        assert(nfaRegex.matches("dcbada"))
        assertFalse(nfaRegex.matches("a"))
        assertFalse(nfaRegex.matches("dcb"))
        assertFalse(nfaRegex.matches("dbad"))
        assertFalse(nfaRegex.matches("dbadc"))
        assertFalse(nfaRegex.matches("dbadcad"))
    }

    @Test
    fun exampleRightRecursion() {
        // for example in paper to show why calls between right and left recursion are
        // different
        // ('a' transition needs to be added to start of automaton for left recursion, but to the
        // end for right recursion of same grammar)

        val grammarDefinition = """
        A -> a
        A -> B
        B -> bA
        """
        val g = grammarStringToGrammar(grammarDefinition)

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val expected = NFA()
        val s0 = expected.addState(isStart = true)
        val s1 = expected.addState(isAcceptingState = true)
        val s2 = expected.addState()
        val s3 = expected.addState()

        s0.addEdge(Edge("ε", null, s3))
        s3.addEdge(Edge("ε", null, s2))
        s2.addEdge(Edge(Regex.escape("b"), null, s3))
        s3.addEdge(Edge(Regex.escape("a"), null, s1))

        assert(expected == automaton)
    }

    @Test
    fun exampleLeftRecursion() {
        // for example in paper to show why calls between right and left recursion are
        // different
        // ('a' transition needs to be added to start of automaton for left recursion, but to the
        // end for right recursion of same grammar)

        val grammarDefinition = """
        A -> a
        A -> B
        B -> Ab
        """
        val g = grammarStringToGrammar(grammarDefinition)

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val expected = NFA()
        val s0 = expected.addState(isStart = true)
        val s1 = expected.addState(isAcceptingState = true)
        val s2 = expected.addState()
        val s3 = expected.addState()

        s0.addEdge(Edge(Regex.escape("a"), null, s3))
        s3.addEdge(Edge(Regex.escape("b"), null, s2))
        s2.addEdge(Edge("ε", null, s3))
        s3.addEdge(Edge("ε", null, s1))

        println("Expected:\n${expected.toDotString()}")

        assertEquals(expected, automaton)
    }

    @Test
    fun exampleNoRecursion() {
        // for example in paper to show why non-recursive single terminals are irrelevant

        val grammarDefinition =
            """
        A -> B
        A -> C
        B -> b
        C -> c
        """
        val g = grammarStringToGrammar(grammarDefinition)
        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val expected = NFA()
        val s0 = expected.addState(isStart = true)
        val s1 = expected.addState(isAcceptingState = true)

        s0.addEdge(Edge(Regex.escape("b"), null, s1))
        s0.addEdge(Edge(Regex.escape("c"), null, s1))

        assertEquals(expected, automaton)
    }
}
