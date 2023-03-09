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
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import org.junit.jupiter.api.*

/** This test class is used to produce the results for the examples used in the thesis. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndStringPropertyTest {
    val path = Path.of("src", "test", "resources", "string_properties_benchmarks")

    @AfterTest
    fun tearDown() {
        StringPropertyHotspots.clear()
    }

    @Test
    fun testTest() {
        buildCPG(listOf(path.resolve("StringProperties.java").toFile()), path)

        StringPropertyHotspots.hotspots.forEach { println("Hotspot: $it") }
        val hotspot_node =
            StringPropertyHotspots.print_hotspots.first {
                it.location.toString().contains("StringProperties.java")
            }
        assertNotNull(hotspot_node)

        val grammar = createGrammar(hotspot_node)
        println("CREATED GRAMMAR:\n${grammar.printGrammar()}")

        CharSetApproximation(grammar).approximate()
        println("CHARSET APPROXIMATION:\n${grammar.printGrammar()}")

        RegularApproximation(grammar).approximate()
        println("REGULAR APPROXIMATION:\n${grammar.printGrammar()}")

        val automaton = GrammarToNFA(grammar).makeFA()
        println("AUTOMATON:\n${automaton.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = automaton.toRegex()
        println("REGEX PATTERN:\n${prettyPrintPattern(pattern)}")

        val regex = Regex(pattern)

        assert(!regex.matches(""))
        assert(!regex.matches("nbcnbc"))
        assert(regex.matches("abc1"))
        assert(regex.matches("nbcnbc1"))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun trickyTest() {
        buildCPG(listOf(path.resolve("Tricky.java").toFile()), path)

        val hotspot_node =
            StringPropertyHotspots.print_hotspots.first {
                it.location.toString().contains("Tricky.java")
            }
        assertNotNull(hotspot_node)

        val (grammar, grammarCreationDuration) = measureTimedValue { createGrammar(hotspot_node) }
        println("CREATED GRAMMAR:\n${grammar.printGrammar()}")

        val charsetApproxDuration = measureTime { CharSetApproximation(grammar).approximate() }

        val regularApproxDuration = measureTime { RegularApproximation(grammar).approximate() }

        val nProds = grammar.getAllNonterminals().flatMap { it.productions }.size
        println(
            "REGULAR APPROXIMATION CONTAINS ${grammar.getAllNonterminals().size} NONTERMINALS WITH $nProds PRODUCTIONS"
        )

        val (automaton, automatonCreationDuration) =
            measureTimedValue { GrammarToNFA(grammar).makeFA() }
        println("NFA:\n${automaton.toDotString().replace("\\Q", "").replace("\\E", "")}")
        println(
            "NFA size: ${automaton.states.size} states, ${automaton.states.flatMap { it.outgoingEdges }.size} transitions"
        )

        val dfa = NFA(automaton.toDfa().states)
        println("DFA:\n${dfa.toDotString().replace("\\Q", "").replace("\\E", "")}")
        println(
            "DFA size: ${dfa.states.size} states, ${dfa.states.flatMap { it.outgoingEdges }.size} transitions"
        )

        val (pattern, toRegexDuration) = measureTimedValue { automaton.toRegex() }
        println("REGEX PATTERN:\n${pattern}\n${prettyPrintPattern(pattern)}")
        println("Pattern length:\n${pattern.length}")

        val dfaPattern = dfa.toRegex()
        println("DFA PATTERN:\n${dfaPattern}\n${prettyPrintPattern(dfaPattern)}")
        println("DFA Pattern length:\n${dfaPattern.length}")

        val regex = Regex(pattern)
        assert(regex.matches("42"))
        assert(regex.matches("(0+1)"))
        assert(regex.matches("((((1*12)*3)*123)"))
        assert(regex.matches("((((1*12)*3)+123)"))
        assertFalse(regex.matches("((((1*12)*3)*123)4"))
        assertFalse(regex.matches("()"))

        val dfaRegex = Regex(dfaPattern)
        assert(dfaRegex.matches("42"))
        assert(dfaRegex.matches("(0+1)"))
        assert(dfaRegex.matches("((((1*12)*3)*123)"))
        assert(dfaRegex.matches("((((1*12)*3)+123)"))
        assertFalse(dfaRegex.matches("((((1*12)*3)*123)4"))
        assertFalse(dfaRegex.matches("()"))

        val total =
            grammarCreationDuration +
                charsetApproxDuration +
                regularApproxDuration +
                automatonCreationDuration +
                toRegexDuration
        println(
            "\nTIMES: GRAMMAR CREATION: $grammarCreationDuration, CHARSET APPROXIMATION: $charsetApproxDuration, REGULAR APPROXIMATION: $regularApproxDuration, AUTOMATON CREATION: $automatonCreationDuration, TO REGEX: $toRegexDuration\nTOTAL: $total"
        )
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun databaseSanitizationTest() {
        buildCPG(listOf(path.resolve("DatabaseSanitization.java").toFile()), path)

        val hotspot_node = StringPropertyHotspots.database_hotspots.first()
        assertNotNull(hotspot_node)

        val (grammar, grammarCreationDuration) = measureTimedValue { createGrammar(hotspot_node) }
        println("CREATED GRAMMAR:\n${grammar.printGrammar()}")

        val charsetApproxDuration = measureTime { CharSetApproximation(grammar).approximate() }

        val regularApproxDuration = measureTime { RegularApproximation(grammar).approximate() }

        val nProds = grammar.getAllNonterminals().flatMap { it.productions }.size
        println(
            "REGULAR APPROXIMATION CONTAINS ${grammar.getAllNonterminals().size} NONTERMINALS WITH $nProds PRODUCTIONS"
        )

        val (automaton, automatonCreationDuration) =
            measureTimedValue { GrammarToNFA(grammar).makeFA() }
        println("NFA:\n${automaton.toDotString().replace("\\Q", "").replace("\\E", "")}")
        println(
            "NFA size: ${automaton.states.size} states, ${automaton.states.flatMap { it.outgoingEdges }.size} transitions"
        )

        val dfa = NFA(automaton.toDfa().states)
        println("DFA:\n${dfa.toDotString().replace("\\Q", "").replace("\\E", "")}")
        println(
            "DFA size: ${dfa.states.size} states, ${dfa.states.flatMap { it.outgoingEdges }.size} transitions"
        )

        val (pattern, toRegexDuration) = measureTimedValue { automaton.toRegex() }
        println("REGEX PATTERN:\n${pattern}\n${prettyPrintPattern(pattern)}")
        println("Pattern length:\n${pattern.length}")

        val dfaPattern = dfa.toRegex()
        println("DFA PATTERN:\n${dfaPattern}\n${prettyPrintPattern(dfaPattern)}")
        println("DFA Pattern length:\n${dfaPattern.length}")

        val total =
            grammarCreationDuration +
                charsetApproxDuration +
                regularApproxDuration +
                automatonCreationDuration +
                toRegexDuration
        println(
            "\nTIMES: GRAMMAR CREATION: $grammarCreationDuration, CHARSET APPROXIMATION: $charsetApproxDuration, REGULAR APPROXIMATION: $regularApproxDuration, AUTOMATON CREATION: $automatonCreationDuration, TO REGEX: $toRegexDuration\nTOTAL: $total"
        )
    }

    @Test
    fun testMinimalTrickyAutomatonRegex() {
        // manual definition of the automaton obtained by minimizing the tricky DFA
        val q0 = State(0, isStart = true)
        val q1 = State(1, isAcceptingState = true)
        val q2 = State(2)
        val q3 = State(3)

        q0.addEdge(Edge("\\Q(\\E", nextState = q0))
        q0.addEdge(Edge("0|(-?[1-9][0-9]*)", nextState = q1))
        q1.addEdge(Edge("\\Q*\\E", nextState = q2))
        q1.addEdge(Edge("\\Q+\\E", nextState = q2))
        q2.addEdge(Edge("0|(-?[1-9][0-9]*)", nextState = q3))
        q3.addEdge(Edge("\\Q)\\E", nextState = q1))
        val dfa = NFA(setOf(q0, q1, q2, q3))
        println("DFA:\n${dfa.toDotString()}")
        println(
            "DFA size: ${dfa.states.size} states, ${dfa.states.flatMap { it.outgoingEdges }.size} transitions"
        )
        val dfaPattern = dfa.toRegex()
        println("DFA PATTERN:\n${dfaPattern}\n${prettyPrintPattern(dfaPattern)}")
        println("DFA Pattern length:\n${dfaPattern.length}")

        val regex = Regex(dfaPattern)
        assert(regex.matches("42"))
        assert(regex.matches("(0+1)"))
        assert(regex.matches("((((1*12)*3)*123)"))
        assert(regex.matches("((((1*12)*3)+123)"))
        assertFalse(regex.matches("((((1*12)*3)*123)4"))
        assertFalse(regex.matches("()"))
    }
}
