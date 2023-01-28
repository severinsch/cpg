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

    private fun prettyPrintPattern(pattern: String): String {
        return pattern.replace("\\Q", "").replace("\\E", "")
    }

    @Test
    fun example1() {
        // original grammar
        // S -> T S
        // S -> a
        // T -> S P
        // P -> +
        val g = Grammar()
        val S = Nonterminal(0, label = "S")
        val T = Nonterminal(1, label = "T")
        val P = Nonterminal(2, label = "P")
        S.addProduction(ConcatProduction(T, S))
        S.addProduction(TerminalProduction(Terminal("a")))
        T.addProduction(ConcatProduction(S, P))
        P.addProduction(TerminalProduction(Terminal("+")))
        listOf(S, T, P).forEach { g.addNonterminal(it) }
        g.startNonterminal = S

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
    fun test2() {
        // aus nederhof paper
        // S -> Aa
        // A -> SB
        // A -> Bb
        // B -> Bc
        // B -> d

        // unsere darstellung:
        // S -> AT
        // A -> SB
        // A -> BU
        // B -> BV
        // B -> W
        // T -> a
        // U -> b
        // V -> c
        // W -> d

        val g = Grammar()
        val S = Nonterminal(0, label = "S")
        val A = Nonterminal(1, label = "A")
        val B = Nonterminal(2, label = "B")
        val T = Nonterminal(3, label = "T")
        val U = Nonterminal(4, label = "U")
        val V = Nonterminal(5, label = "V")
        val W = Nonterminal(6, label = "W")
        S.addProduction(ConcatProduction(A, T))
        A.addProduction(ConcatProduction(S, B))
        A.addProduction(ConcatProduction(B, U))
        B.addProduction(ConcatProduction(B, V))
        B.addProduction(UnitProduction(W))
        T.addProduction(TerminalProduction(Terminal("a")))
        U.addProduction(TerminalProduction(Terminal("b")))
        V.addProduction(TerminalProduction(Terminal("c")))
        W.addProduction(TerminalProduction(Terminal("d")))

        listOf(S, A, B, T, U, V, W).forEach { g.addNonterminal(it) }
        g.startNonterminal = S

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
}
