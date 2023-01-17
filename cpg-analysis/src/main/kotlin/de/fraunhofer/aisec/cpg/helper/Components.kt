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

import kotlin.math.min

enum class Recursion {
    NONE,
    RIGHT,
    LEFT,
    BOTH
}

class Component {
    var recursion: Recursion = Recursion.NONE
    val nonterminals: MutableCollection<Nonterminal> = mutableSetOf()

    fun determineRecursion() {
        for (nt in nonterminals) {
            for (prod in nt.productions) {
                if (prod is ConcatProduction) {
                    if (prod.target1 in nonterminals) {
                        addRecursion(Recursion.LEFT)
                    }
                    if (prod.target2 in nonterminals) {
                        addRecursion(Recursion.RIGHT)
                    }
                }
            }
        }
    }

    operator fun contains(nt: Nonterminal): Boolean {
        return nonterminals.contains(nt)
    }

    private fun addRecursion(r: Recursion) {
        if (recursion == Recursion.NONE) {
            recursion = r
        }
        if (recursion != r) {
            recursion = Recursion.BOTH
        }
    }
}

class SCC(private val grammar: Grammar) {
    val components: MutableList<Component> = mutableListOf()
    private val componentsForNodes: MutableMap<Nonterminal, Component> = mutableMapOf()
    private val lowlink = mutableMapOf<Nonterminal, Int>()
    private val index = mutableMapOf<Nonterminal, Int>()
    private val nodes = grammar.getAllNonterminals()

    /** performs tarjans algorithm to find the strongly connected components of [grammar] */
    init {
        val stack = ArrayDeque<Nonterminal>()
        for (node in nodes) {
            if (node !in index) {
                visit(node, 0, stack)
            }
        }
    }

    private fun visit(currentNT: Nonterminal, c: Int, stack: ArrayDeque<Nonterminal>): Int {
        var count = c
        lowlink[currentNT] = count
        index[currentNT] = count
        count++
        stack.addLast(currentNT)
        for (neighbor in grammar.getSuccessorsFor(currentNT)) {
            if (neighbor !in index) {
                count = visit(neighbor, count, stack)
                lowlink[currentNT] = min(lowlink[neighbor]!!, lowlink[currentNT]!!)
            } else if (neighbor in stack) {
                lowlink[currentNT] = min(lowlink[currentNT]!!, index[neighbor]!!)
            }
        }
        if (lowlink[currentNT] == index[currentNT]) {
            val comp = Component()
            components.add(comp)

            do {
                val x = stack.removeLast()
                comp.nonterminals.add(x)
                componentsForNodes[x] = comp
            } while (x != currentNT)
        }
        return count
    }

    fun getComponentForNonterminal(node: Nonterminal): Component? {
        return componentsForNodes[node]
    }

    override fun toString(): String {
        return components.joinToString(separator = ";") { c ->
            "\n{" + c.nonterminals.joinToString(separator = ",") { n -> n.id.toString() } + "}"
        }
    }
}
