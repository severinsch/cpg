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
package de.fraunhofer.aisec.cpg.helper.operations

import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet

class Trim : Operation(1) {
    override fun toString(): String {
        return "trim"
    }

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        return
        // TODO this is wrong :(
        // brics add words without whitespace to the language by adding transitions
        /* val incomingTaintedEdges: MutableMap<State, MutableSet<Pair<State, Edge>>> = mutableMapOf()
        affectedStates.forEach { state ->
            state.outgoingEdges.forEach { edge ->
                if (edge.taints.any { it.operation == this }) {
                    incomingTaintedEdges
                        .getOrPut(edge.nextState) { mutableSetOf() }
                        .add(state to edge)
                }
            }
        }
        println(affectedStates)
        println(incomingTaintedEdges[affectedStates.first()])
        val startState = affectedStates.first { s -> incomingTaintedEdges[s].isNullOrEmpty() }
        val endStates =
            affectedStates.filter { s ->
                s.outgoingEdges.none { edge -> edge.taints.any { it.operation == this } }
            }

        fun trimStart(state: State, visited: MutableSet<State>) {
            if (state in visited) {
                return
            }
            visited.add(state)
            println("start edges b4: ${state.outgoingEdges}")
            state.outgoingEdges =
                state.outgoingEdges
                    .map { edge ->
                        if (edge.taints.any { it.operation == this }) {
                            println("trimStart edge from $state to ${edge.nextState}")
                            if (edge.op.isBlank() || edge.op == "ε") {
                                trimStart(edge.nextState, visited)
                            }
                            return@map edge.copy(op = edge.op.trimStart())
                        }
                        return@map edge
                    }
                    .toSet()
            println("start edges b4: ${state.outgoingEdges}")
        }

        fun trimEnd(state: State, visited: MutableSet<State>) {
            if (state in visited) {
                return
            }
            visited.add(state)
            val edges = incomingTaintedEdges[state] ?: return

            edges.forEach { (prev, edge) ->
                prev.outgoingEdges =
                    prev.outgoingEdges
                        .map {
                            if (it == edge) {
                                if (edge.taints.any { it.operation == this }) {
                                    println("trimEnd edge from $prev to ${edge.nextState}")
                                    if (edge.op.isBlank() || edge.op == "ε") {
                                        trimEnd(prev, visited)
                                    }
                                    return@map edge.copy(op = edge.op.trimEnd())
                                }
                            }
                            return@map it
                        }
                        .toSet()
            }
        }

        trimStart(startState, mutableSetOf())
        endStates.forEach { trimEnd(it, mutableSetOf()) }

         */
    }

    override fun charsetTransformation(cs: CharSet): CharSet {
        return cs
    }
}
