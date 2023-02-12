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

import de.fraunhofer.aisec.cpg.analysis.fsm.Edge
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet

class Reverse : Operation(1) {

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        if (affectedStates.isEmpty()) return

        val stateMap = affectedStates.associateWith { s -> automaton.addState() }
        stateMap.entries.forEach { (old, new) ->
            new.outgoingEdges =
                old.outgoingEdges
                    .filter { edge -> edge.taints.any { it.operation == this } }
                    .map { edge -> edge.copy(nextState = stateMap[edge.nextState]!!) }
                    .toSet()
        }
        val subAutomatonStates = stateMap.values.toMutableSet()

        val reachableStates =
            subAutomatonStates.flatMap { it.outgoingEdges.map { e -> e.nextState } }
        subAutomatonStates.removeAll { it.outgoingEdges.isEmpty() && it !in reachableStates }

        val startState =
            subAutomatonStates.first { s ->
                subAutomatonStates
                    .filter { it != s }
                    .all { otherS -> s !in otherS.outgoingEdges.map { it.nextState } }
            }

        val endStates = subAutomatonStates.filter { s -> s.outgoingEdges.isEmpty() }
        val newStart = automaton.addState()

        startState.isStart = false
        startState.isAcceptingState = endStates.any { it.isAcceptingState } // TODO ?

        endStates.forEach { newStart.addEdge(Edge("ε", nextState = it)) }

        println(
            "Reverse subautomaton start state: $startState, end states: $endStates, new start state: $newStart"
        )
        println(
            "Subautomaton:\n${NFA(subAutomatonStates + newStart).toDotString().replace("\\Q", "").replace("\\E", "")}\n"
        )

        // reverse edges in subautomaton
        val oldEdges = subAutomatonStates.flatMap { s -> s.outgoingEdges.map { s to it } }
        subAutomatonStates.forEach { it.outgoingEdges = emptySet() }
        for ((from, edge) in oldEdges) {
            val newEdge = edge.copy(nextState = from)
            edge.nextState.addEdge(newEdge)
        }
        val subAutomaton = NFA(subAutomatonStates + newStart)
        println(
            "Reverse Subautomaton:\n${subAutomaton.toDotString().replace("\\Q", "").replace("\\E", "")}\n"
        )

        // put reversed automaton into original automaton

        automaton.states
            .filter { it !in subAutomatonStates }
            .forEach { s ->
                s.outgoingEdges =
                    s.outgoingEdges
                        .filter {
                            it.taints.none { t -> t.operation == this } // remove duplicated edges
                        }
                        .flatMap { e ->
                            if (stateMap[e.nextState] == startState) {
                                return@flatMap listOf(e, e.copy(nextState = newStart))
                            }
                            return@flatMap listOf(e)
                        }
                        .toSet()
            }
        if (automaton.states.first { it.isStart } in affectedStates) {
            automaton.states.first { it.isStart }.addEdge(Edge("ε", nextState = newStart))
        }
        // edges from original start state to original targets of end states
        automaton.states
            .filter { stateMap[it] in endStates }
            .forEach { startState.addEdge(Edge("ε", nextState = it)) }

        val origStartOutgoing =
            automaton.states
                .first { stateMap[it] == startState }
                .outgoingEdges
                .filter { it.taints.none { t -> t.operation == this } }
        origStartOutgoing.forEach { newStart.addEdge(it) }

        // remove unreachable states in resulting automaton
        val reachableStatesNFA =
            automaton.states.flatMap { s -> s.outgoingEdges.map { it.nextState } }.toSet()
        automaton.states
            .filter { it.outgoingEdges.isEmpty() && it !in reachableStatesNFA }
            .forEach { automaton.removeState(it) }
    }

    override fun charsetTransformation(cs: CharSet): CharSet = cs
    override fun toString() = "reverse"
}
