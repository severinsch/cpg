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

import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
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

        println("Initial Grammar:\n${g.printGrammar()}")

        CharSetApproximation(g).approximate()
        println("After Charset Approximation:\n${g.printGrammar()}") // equal to initial

        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")
        // Grammar after RegularApproximation:
        // S -> T
        // S -> R S'
        // T -> S
        // P -> "+"
        // T' -> S
        // S' -> ""
        // S' -> P T'
        // S' -> S'
        // R -> "a"

        val automaton = GrammarToNFA(g).makeFA()
        println("Initial automaton: ${ automaton.toDotString() }")

        // Two possibilities:
        // use NFA directly: produces good results for this example only if delgado heuristic is
        // used
        // convert to DFA: creates optimal automaton for this example and produces good result even
        // without heuristic
        // BUT: NFA -> DFA potential exponential blowup

        val dfa = automaton.toDfa()
        val dfaAsNfa = NFA(dfa.states) // same automaton, just to make typechecking work
        println("DFA: ${ dfaAsNfa.toDotString() }")

        val dfaRegexPattern = dfaAsNfa.toRegex()
        println("Regex from DFA: ${prettyPrintPattern(dfaRegexPattern)}")

        val nfaRegexPattern = automaton.toRegex()
        println("Regex from NFA: ${prettyPrintPattern(nfaRegexPattern)}")

        val nfaRegexPatternWithoutHeuristic = automaton.toRegex { _ -> 0 }
        println(
            "Regex from NFA without heuristic: ${prettyPrintPattern(nfaRegexPatternWithoutHeuristic)}"
        )

        val nfaRegex = Regex(nfaRegexPattern)
        assert(nfaRegex.matches("a+a+a+a"))
        assert(nfaRegex.matches("a"))
        assertFalse(nfaRegex.matches(""))
        assertFalse(nfaRegex.matches("a+a+"))
        assertFalse(nfaRegex.matches("+a+a"))
    }

    @Test
    fun example3() {
        // aus nederhof paper
        // S -> Aa
        // A -> SB
        // A -> Bb
        // B -> Bc
        // B -> d
        val grammarDefinition =
            """
            S -> Aa
            A -> SB
            A -> Bb
            B -> Bc
            B -> d
        """.trimIndent()

        val g = grammarStringToGrammar(grammarDefinition)

        println("Initial Grammar:\n${g.printGrammar()}")

        RegularApproximation(g, setOf(0)).approximate()
        println("Approximated grammar:\n${g.printGrammar()}")

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val dfa = automaton.toDfa()
        val dfaAsNfa = NFA(dfa.states) // same automaton, just to make typechecking work
        println("DFA: ${ dfaAsNfa.toDotString() }")

        val dfaRegexPattern = dfaAsNfa.toRegex()
        println("Regex from DFA: ${prettyPrintPattern(dfaRegexPattern)}")

        val nfaRegexPattern = automaton.toRegex()
        println("Regex from NFA: ${prettyPrintPattern(nfaRegexPattern)}")
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

        println("Initial Grammar:\n${g.printGrammar()}")

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val nfaRegexPattern = automaton.toRegex()
        println("Regex from NFA: ${prettyPrintPattern(nfaRegexPattern)}")
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

        println("Initial Grammar:\n${g.printGrammar()}")

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val nfaRegexPattern = automaton.toRegex()
        println("Regex from NFA: ${prettyPrintPattern(nfaRegexPattern)}")
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

        println("Initial Grammar:\n${g.printGrammar()}")

        val automaton = GrammarToNFA(g).makeFA()
        println("Automaton:\n${automaton.toDotString()}")

        val nfaRegexPattern = automaton.toRegex()
        println("Regex from NFA: ${prettyPrintPattern(nfaRegexPattern)}")
    }
}
