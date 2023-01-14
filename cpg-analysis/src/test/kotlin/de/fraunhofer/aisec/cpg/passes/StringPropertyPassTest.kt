/*
 * Copyright (c) 2022, Fraunhofer AISEC. All rights reserved.
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
package de.fraunhofer.aisec.cpg.passes

import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.TranslationManager
import de.fraunhofer.aisec.cpg.frontends.java.JavaLanguage
import de.fraunhofer.aisec.cpg.graph.*
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.CompoundStatement
import de.fraunhofer.aisec.cpg.graph.statements.ForStatement
import de.fraunhofer.aisec.cpg.graph.statements.expressions.DeclaredReferenceExpression
import de.fraunhofer.aisec.cpg.helper.*
import de.fraunhofer.aisec.cpg.helper.approximations.CharSetApproximation
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass.*
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StringPropertyPassTest {
    private lateinit var tu: TranslationUnitDeclaration

    @BeforeAll
    fun beforeAll() {
        val topLevel = Path.of("src", "test", "resources", "passes.string_properties")
        TranslationManager.builder().build().analyze()
        tu =
            TestUtils.analyzeAndGetFirstTU(
                listOf(topLevel.resolve("StringProperties.java").toFile()),
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
        val hotspot_node = StringPropertyHotspots.print_hotspots.first()
        assertNotNull(hotspot_node)
        val cfg = createGrammar(hotspot_node)
        println("GRAMMAR CREATED FROM hotspot node with id ${hotspot_node.id}:")
        println(cfg.printGrammar())
        val scc = SCC(cfg)
        // (scc)
        // println(cfg.toDOT(scc))
        val csa = CharSetApproximation(cfg)
        csa.approximate()
        val scc2 = SCC(cfg)
        println(cfg.toDOT(scc2))
    }

    private fun getTestNode(id: Long): Node {
        val n = Node()
        n.id = id
        return n
    }

    @Test
    fun testSCCCreation() {
        val g = ContextFreeGrammar()

        val nodes = (0..9).map { getTestNode(it.toLong()) }

        val nt1 = Nonterminal(1)
        nt1.addProduction(UnitProduction(nodes[2])) // a -> b
        g.addNonterminal(1, nt1)

        val nt2 = Nonterminal(2)
        nt2.addProduction(UnitProduction(nodes[3])) // b -> c
        nt2.addProduction(ConcatProduction(nodes[5], nodes[6])) // b -> e, b -> f
        g.addNonterminal(2, nt2)

        val nt3 = Nonterminal(3)
        nt3.addProduction(UnitProduction(nodes[4])) // c-> d
        nt3.addProduction(UnitProduction(nodes[7])) // c->g
        g.addNonterminal(3, nt3)

        val nt4 = Nonterminal(4)
        nt4.addProduction(UnitProduction(nodes[3])) // d -> c
        nt4.addProduction(UnitProduction(nodes[8])) // d -> h
        g.addNonterminal(4, nt4)

        val nt5 = Nonterminal(5)
        nt5.addProduction(ConcatProduction(nodes[1], nodes[6])) // e -> a, e -> f
        g.addNonterminal(5, nt5)

        val nt6 = Nonterminal(6)
        nt6.addProduction(UnitProduction(nodes[7])) // f -> g
        g.addNonterminal(6, nt6)

        val nt7 = Nonterminal(7)
        nt7.addProduction(UnitProduction(nodes[6])) // g -> f
        g.addNonterminal(7, nt7)

        val nt8 = Nonterminal(8)
        nt8.addProduction(UnitProduction(nodes[7])) // h -> g
        nt8.addProduction(UnitProduction(nodes[4])) // h -> d
        g.addNonterminal(8, nt8)

        val nt9 = Nonterminal(9)
        nt9.addProduction(UnitProduction(nodes[8])) //
        g.addNonterminal(9, nt9)

        val scc = SCC(g)

        // components should be {1, 2, 5}, {6, 7}, {8, 4, 3}, {9}
        assertEquals(4, scc.components.size)
        val compSets =
            scc.components.map { comp -> comp.nonterminal.map { nt -> nt.id }.toSet() }.toSet()
        val expected: Set<Set<Long>> = setOf(setOf(1, 2, 5), setOf(6, 7), setOf(8, 4, 3), setOf(9))
        assertEquals(expected, compSets)
    }

    @Test
    fun testPassCollectsCorrectHotspots() {
        // correct amount
        assert(StringPropertyHotspots.hotspots.size == 3)
        assert(StringPropertyHotspots.database_hotspots.size == 1)
        assert(StringPropertyHotspots.print_hotspots.size == 1)
        assert(StringPropertyHotspots.return_hotspots.size == 1)

        val printCall = tu.calls["println"]
        val finalReference = printCall?.arguments?.get(0) as? DeclaredReferenceExpression
        assertNotNull(finalReference)

        val hotspotPrintRef = StringPropertyHotspots.print_hotspots.first()
        assert(finalReference == hotspotPrintRef)

        val v = finalReference.evaluate()
        // way to figure out how many times loop is executed?
        // -> maybe modify grammar after creation for loop/if
        println(
            ((tu.functions["myFun"]?.body as CompoundStatement).statements[1] as ForStatement)
                .locals[0]
                .type
                .typeName
        )
    }
}
