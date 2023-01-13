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
package de.fraunhofer.aisec.cpg.helper

import java.util.*
import kotlin.math.min

enum class Recursion {
    NONE,
    RIGHT,
    LEFT,
    BOTH
}

class Component {
    val recursion: Recursion = Recursion.NONE
    val nonterminal: MutableCollection<Nonterminal> = mutableSetOf()

    fun determineRecursion() {
        TODO()
    }
}

class SCC(private val grammar: ContextFreeGrammar) {
    val components: MutableList<Component> = mutableListOf()
    private val componentsForNodes: MutableMap<Nonterminal, Component> = mutableMapOf()
    private val lowlink = mutableMapOf<Nonterminal, Int>()
    private val index = mutableMapOf<Nonterminal, Int>()
    private val nodes = grammar.nonterminals.values

    /** performs tarjans algorithm to find the strongly connected components of [grammar] */
    init {

        fun visit(node: Nonterminal, c: Int, stack: Stack<Nonterminal>): Int {
            var count = c
            lowlink[node] = count
            index[node] = count
            count++
            stack.push(node)
            for (neighbor in grammar.getSuccessorsFor(node)) {
                if (!index.contains(neighbor)) {
                    count = visit(neighbor, count, stack)
                    lowlink[node] = min(lowlink[neighbor]!!, lowlink[node]!!)
                } else if (stack.contains(neighbor)) {
                    lowlink[node] = min(lowlink[node]!!, index[neighbor]!!)
                }
            }
            if (lowlink[node] == index[node]) {
                val comp = Component()
                components.add(comp)

                do {
                    val x = stack.pop()
                    comp.nonterminal.add(x)
                    componentsForNodes[x] = comp
                } while (x != node)
            }
            return count
        }

        val stack = Stack<Nonterminal>()
        for (node in nodes) {
            if (!index.contains(node)) {
                visit(node, 0, stack)
            }
        }
    }

    fun getComponentForNonterminal(node: Nonterminal): Component? {
        return componentsForNodes[node]
    }

    override fun toString(): String {
        return components.joinToString(separator = ";") { c ->
            "\n{" + c.nonterminal.joinToString(separator = ",") { n -> n.id.toString() } + "}"
        }
    }
}

class GrammarGraph(val grammar: ContextFreeGrammar) {

    fun getStronglyConnectedComponents(): SCC {
        return SCC(grammar)
    }
}
