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
import de.fraunhofer.aisec.cpg.helper.operations.ReplaceBothKnown
import de.fraunhofer.aisec.cpg.helper.operations.Reverse
import de.fraunhofer.aisec.cpg.helper.operations.ToUpperCase
import de.fraunhofer.aisec.cpg.helper.operations.Trim
import org.junit.jupiter.api.Test

class OperationTest {

    @Test
    fun trimTest() {
        // A -> op(F)
        // F -> WX
        // F -> XW
        // F -> XF
        // X -> x
        // W -> " "

        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val F = Nonterminal(1, label = "F")
        val X = Nonterminal(2, label = "X")
        val W = Nonterminal(3, label = "W")

        A.addProduction(UnaryOpProduction(Trim(), F))
        F.addProduction(ConcatProduction(W, X))
        F.addProduction(ConcatProduction(X, W))
        F.addProduction(ConcatProduction(X, F))
        X.addProduction(TerminalProduction(Terminal("x")))
        W.addProduction(TerminalProduction(Terminal(" ")))
        listOf(A, F, X, W).forEach { g.addNonterminal(it) }
        g.startNonterminal = A

        CharSetApproximation(g).approximate()
        RegularApproximation(g, setOf(0)).approximate()

        val nfaNoOps = GrammarToNFA(g).makeFA(false)
        println("NFA:\n${nfaNoOps.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val nfa = GrammarToNFA(g).makeFA(true)
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }

    @Test
    fun example2() {

        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val C = Nonterminal(1, label = "C")
        val D = Nonterminal(2, label = "D")
        val TB = Nonterminal(3, label = "TB")
        val TA = Nonterminal(4, label = "TA")
        val TD = Nonterminal(5, label = "TD")
        val E = Nonterminal(6, label = "E")
        val TE = Nonterminal(7, label = "TE")
        val F = Nonterminal(8, label = "F")
        val TF = Nonterminal(9, label = "TF")
        val K = Nonterminal(10, label = "K")
        A.addProduction(UnitProduction(C))
        C.addProduction(ConcatProduction(TA, A))
        C.addProduction(ConcatProduction(TB, D))
        D.addProduction(ConcatProduction(E, TD))
        // E.addProduction(ConcatProduction(D, TE))
        E.addProduction(UnitProduction(K))
        E.addProduction(UnaryOpProduction(ReplaceBothKnown('f', 'x'), K))
        K.addProduction(ConcatProduction(TF, F))
        F.addProduction(UnitProduction(TF))
        F.addProduction(UnitProduction(K))
        TF.addProduction(TerminalProduction(Terminal("f")))
        TE.addProduction(TerminalProduction(Terminal("e")))
        TB.addProduction(TerminalProduction(Terminal("b")))
        TA.addProduction(TerminalProduction(Terminal("a")))
        TD.addProduction(TerminalProduction(Terminal("d")))
        listOf(A, C, D, TB, TA, TD, TE, E, F, K, TF).forEach { g.addNonterminal(it) }
        g.startNonterminal = A
        println("Initial grammar:\n${g.printGrammar()}")

        CharSetApproximation(g).approximate()
        println("After CharSet Approximation:\n${g.printGrammar()}")

        RegularApproximation(g, setOf(0)).approximate()
        println("After Regular Approximation:\n${g.printGrammar()}")

        val nfa = GrammarToNFA(g).makeFA()
        println("NFA:\n${nfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = nfa.toRegex()
        println("Pattern: ${prettyPrintPattern(pattern)}")
    }

    @Test
    fun exampleOperationProduction() {
        // A -> F
        // A -> op(F)
        // F -> fF
        // F -> f

        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val F = Nonterminal(2, label = "F")
        val TF = Nonterminal(3, label = "TF")

        A.addProduction(UnitProduction(F))
        A.addProduction(UnaryOpProduction(ReplaceBothKnown('f', 'x'), F))
        F.addProduction(ConcatProduction(TF, F))
        F.addProduction(UnitProduction(TF))
        TF.addProduction(TerminalProduction(Terminal("f")))
        listOf(A, F, TF).forEach { g.addNonterminal(it) }
        g.startNonterminal = A
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
        // A -> op(C)
        // C -> op(D)
        // D -> d
        // C -> op(E)
        // E -> K
        // E -> op(K)
        // K -> fF
        // F -> f
        // F -> K

        val g = Grammar()
        val A = Nonterminal(0, label = "A")
        val C = Nonterminal(1, label = "C")
        val D = Nonterminal(2, label = "D")

        val E = Nonterminal(6, label = "E")
        val F = Nonterminal(8, label = "F")
        val TF = Nonterminal(9, label = "TF")
        val K = Nonterminal(10, label = "K")
        A.addProduction(UnaryOpProduction(Trim(), C))
        C.addProduction(UnaryOpProduction(ToUpperCase(), D))
        D.addProduction(TerminalProduction(Terminal("d")))
        C.addProduction(UnaryOpProduction(Reverse(), E))
        E.addProduction(UnitProduction(K))
        E.addProduction(UnaryOpProduction(ReplaceBothKnown('f', 'x'), K))
        K.addProduction(ConcatProduction(TF, F))
        F.addProduction(UnitProduction(TF))
        F.addProduction(UnitProduction(K))
        TF.addProduction(TerminalProduction(Terminal("f")))
        listOf(A, C, D, E, F, K, TF, TF).forEach { g.addNonterminal(it) }
        g.startNonterminal = A
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
        // S -> X
        // X -> xS
        // X -> A
        // A -> op(C)
        // C -> op(D)
        // D -> d
        // C -> op(E)
        // E -> K
        // E -> op(K)
        // K -> fF
        // F -> f
        // F -> K

        val g = Grammar()
        val S = Nonterminal(11, label = "S")
        val X = Nonterminal(12, label = "X")
        val TX = Nonterminal(13, label = "TX")
        val A = Nonterminal(0, label = "A")
        val C = Nonterminal(1, label = "C")
        val D = Nonterminal(2, label = "D")

        val E = Nonterminal(6, label = "E")
        val F = Nonterminal(8, label = "F")
        val TF = Nonterminal(9, label = "TF")
        val TA = Nonterminal(14, label = "TA")
        val K = Nonterminal(10, label = "K")
        S.addProduction(UnitProduction(X))
        X.addProduction(ConcatProduction(TX, S))
        X.addProduction(UnitProduction(A))
        TX.addProduction(TerminalProduction(Terminal("x")))
        A.addProduction(UnaryOpProduction(Trim(), C))
        C.addProduction(UnaryOpProduction(ToUpperCase(), D))
        D.addProduction(TerminalProduction(Terminal("d")))
        C.addProduction(UnaryOpProduction(Reverse(), E))
        E.addProduction(UnitProduction(K))
        E.addProduction(UnaryOpProduction(ReplaceBothKnown('f', 'x'), K))
        K.addProduction(ConcatProduction(TF, F))
        F.addProduction(UnitProduction(TA))
        F.addProduction(UnitProduction(K))
        TF.addProduction(TerminalProduction(Terminal("f")))
        TA.addProduction(TerminalProduction(Terminal("a")))
        listOf(A, C, D, E, F, K, TF, TA, S, X).forEach { g.addNonterminal(it) }
        g.startNonterminal = S
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
}
