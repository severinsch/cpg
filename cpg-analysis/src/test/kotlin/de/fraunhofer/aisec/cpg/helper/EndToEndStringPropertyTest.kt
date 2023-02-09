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

import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.TranslationManager
import de.fraunhofer.aisec.cpg.TranslationResult
import de.fraunhofer.aisec.cpg.analysis.fsm.Edge
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.frontends.java.JavaLanguage
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.helper.approximations.RegularApproximation
import de.fraunhofer.aisec.cpg.helper.automaton.GrammarToNFA
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.nio.file.Path
import kotlin.test.assertNotNull
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndStringPropertyTest {
    private lateinit var tr: TranslationResult

    @BeforeAll
    fun beforeAll() {
        val topLevel = Path.of("src", "test", "resources", "passes.string_properties")
        TranslationManager.builder().build().analyze()
        tr =
            TestUtils.analyze(
                listOf(
                    topLevel.resolve("Tricky.java").toFile(),
                    // topLevel.resolve("StringProperties.java").toFile(),
                    ),
                topLevel,
                true
            ) {
                it.registerLanguage<JavaLanguage>()
                    .registerPass(IdentifierPass())
                    .registerPass(EdgeCachePass())
                    .registerPass(StringPropertyPass())
            }
    }

    @Test
    fun testTest() {
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

    @Test
    fun trickyTest() {
        val hotspot_node =
            StringPropertyHotspots.print_hotspots.first {
                it.location.toString().contains("Tricky.java")
            }
        assertNotNull(hotspot_node)

        val grammar = createGrammar(hotspot_node)
        println("CREATED GRAMMAR:\n${grammar.printGrammar()}")

        CharSetApproximation(grammar).approximate()

        RegularApproximation(grammar).approximate()

        val nProds = grammar.getAllNonterminals().flatMap { it.productions }.size
        println(
            "REGULAR APPROXIMATION CONTAINS ${grammar.getAllNonterminals().size} NONTERMINALS WITH $nProds PRODUCTIONS"
        )

        // val grammarGraph = grammar.toDOT(scc = SCC(grammar))
        // println("GRAMMAR GRAPH REG APPROX:\n${grammarGraph.replace("\\Q", "").replace("\\E",
        // "")}")

        val automaton = GrammarToNFA(grammar).makeFA()
        println("AUTOMATON:\n${automaton.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val dfa = NFA(automaton.toDfa().states)
        println("DFA:\n${dfa.toDotString().replace("\\Q", "").replace("\\E", "")}")

        val pattern = automaton.toRegex()
        println("REGEX PATTERN:\n${pattern}\n${prettyPrintPattern(pattern)}")

        val dfaPattern = dfa.toRegex()
        println("DFA PATTERN:\n${dfaPattern}\n${prettyPrintPattern(dfaPattern)}")

        val regex = Regex(pattern)
        assert(regex.matches("(0+1)"))
        assert(regex.matches("((((1*12)*3)*123)"))
    }

    @Test
    fun testMinimalTrickyAutomatonRegex() {
        val q0 = State(0, isStart = true)
        val q1 = State(1, isAcceptingState = true)
        val q2 = State(2)
        val q3 = State(3)

        q0.addEdge(Edge("\\Q(\\E", nextState = q0))
        q0.addEdge(Edge("<int>", nextState = q1))
        q1.addEdge(Edge("\\Q*\\E", nextState = q2))
        q1.addEdge(Edge("\\Q+\\E", nextState = q2))
        q2.addEdge(Edge("<int>", nextState = q3))
        q3.addEdge(Edge("\\Q)\\E", nextState = q1))
        val dfa = NFA(setOf(q0, q1, q2, q3))
        println("DFA:\n${dfa.toDotString()}")
        val dfaPattern = dfa.toRegex()
        println("DFA PATTERN:\n${dfaPattern}\n${prettyPrintPattern(dfaPattern)}")
    }
}
