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
package de.fraunhofer.aisec.cpg.analysis.fsm

/** A representation of a non-deterministic finite automaton (NFA). */
class NFA(states: Set<State> = setOf()) : FSM(states) {
    companion object {
        @JvmStatic val EPSILON: String = "ε"
    }

    /** Create a shallow copy */
    override fun copy() = NFA(states = states)

    /**
     * Compute the ε-closure for this ε-NFA and then use the
     * [powerset construction](https://en.wikipedia.org/wiki/Powerset_construction) algorithm (
     * [example](https://www.javatpoint.com/automata-conversion-from-nfa-with-null-to-dfa)) to
     * convert it to a [DFA]
     */
    fun toDfa(): DFA {
        /**
         * Recursively compute the ε-closure for the given set of states (i.e., all states reachable
         * by ε-transitions from any of the states in the set)
         */
        tailrec fun getEpsilonClosure(states: MutableSet<State>): Set<State> {
            val newStates =
                states
                    .flatMap { state -> state.outgoingEdges.filter { edge -> edge.op == EPSILON } }
                    .map { it.nextState }
                    .toMutableSet()
            if (states.containsAll(newStates)) {
                return states
            }
            return getEpsilonClosure(states.union(newStates).toMutableSet())
        }

        check(states.count { it.isStart } == 1) {
            "To convert a NFA to a DFA, the NFA must contain exactly one start state"
        }

        val dfa = DFA() // new empty DFA which is incrementally extended
        val epsilonClosures =
            mutableMapOf<
                Set<State>, State
            >() // used to remember which DFA state an ε-closure of NFA states maps to
        val statesToExplore =
            ArrayDeque<
                Pair<State, Set<State>>
            >() // a queue to remember which states still have to be explored

        // Set up the basis on which to explore the current NFA
        // start with finding the ε-closures of the starting state
        val startStateClosure = getEpsilonClosure(mutableSetOf(states.first { it.isStart }))
        // add the new start state to the DFA corresponding to a set of NFA states
        var nextDfaState =
            dfa.addState(
                isStart = startStateClosure.any { it.isStart },
                isAcceptingState = startStateClosure.any { it.isAcceptingState }
            )
        epsilonClosures +=
            startStateClosure to
                nextDfaState // remember which DFA state maps to the startStateClosure
        // and add it to the yet to be explored states
        statesToExplore.add(nextDfaState to startStateClosure)

        // do the same thing for the rest of the NFA
        // by walking through the NFA starting with the start state, this algorithm only converts
        // the
        // reachable part of the NFA
        while (statesToExplore.size > 0) {
            // get the state to explore next (starts with the new start state created above)
            val (currentDfaState, epsilonClosure) = statesToExplore.removeFirst()
            // for each state in the epsilonClosure of the currently explored state, we have to get
            // all possible transitions/edges
            // and group them by their 'name' (the base and op attributes)
            val allPossibleEdges =
                epsilonClosure
                    .flatMap { state -> state.outgoingEdges.filter { edge -> edge.op != EPSILON } }
                    .groupBy { it.base to it.op }
            // then we follow each transition/edge for the current epsilonClosure
            for ((transitionBaseToOp, edges) in allPossibleEdges) {
                val (transitionBase, transitionOp) = transitionBaseToOp
                // because multiple states in the current epsilonClosure might have edges with the
                // same 'name' but to different states
                // we again have to get the epsilonClosure of the target states
                val transitionClosure = getEpsilonClosure(edges.map { it.nextState }.toMutableSet())
                if (transitionClosure in epsilonClosures) {
                    // if the transitionClosure is already in the DFA, get the DFA state it
                    // corresponds to
                    nextDfaState = epsilonClosures[transitionClosure]!!
                } else {
                    // else create a new DFA state and add it to the known and to be explored states
                    nextDfaState =
                        dfa.addState(
                            isAcceptingState = transitionClosure.any { it.isAcceptingState }
                        )
                    statesToExplore.add(nextDfaState to transitionClosure)
                    epsilonClosures += transitionClosure to nextDfaState
                }
                // either way, we must create an edge connecting the states
                currentDfaState.addEdge(
                    Edge(
                        base = transitionBase,
                        op = transitionOp,
                        nextState = nextDfaState,
                    )
                )
            }
        }
        return dfa
    }

    // used for delgado heuristic
    private fun getAllIncomingEdges(state: State): List<Edge> {
        return states
            .filter { it != state }
            .flatMap { it.outgoingEdges }
            .filter { it.nextState == state }
    }

    // used for delgado heuristic
    private fun getAllOutgoingEdges(state: State): List<Edge> {
        return state.outgoingEdges.filter { it.nextState != state }
    }

    private fun delgadoHeuristic(state: State): Int {
        // heuristic described in https://link.springer.com/chapter/10.1007/978-3-540-30500-2_31
        // despite additional complexity to calculate weight, they show it produces better results
        // and is faster than without the heuristic

        val loopEdge = state.outgoingEdges.find { it.nextState == state }
        val incomingEdges = getAllIncomingEdges(state)
        val outgoingEdges = getAllOutgoingEdges(state)
        val sumIn =
            incomingEdges.fold(0) { acc, edge ->
                edge.op.filter { it != 'ε' }.length * outgoingEdges.size + acc
            }
        val sumOut =
            outgoingEdges.fold(0) { acc, edge ->
                edge.op.filter { it != 'ε' }.length * incomingEdges.size + acc
            }
        val rest = (loopEdge?.op?.length ?: 0) * incomingEdges.size * outgoingEdges.size
        return sumIn + sumOut + rest
    }

    private fun toOptional(pattern: String): String {
        return if (pattern.isEmpty() || pattern.length == 1) {
            pattern
        } else {
            "($pattern)?"
        }
    }

    fun toRegex(): String {
        return toRegex(::delgadoHeuristic)
    }

    /**
     * Creates a regular expression of the NFA with the state elimination strategy. It enriches the
     * edges to retrieve a GNFA and finally has a regex. Unfortunately, it is not optimized or super
     * readable.
     */
    fun toRegex(heuristic: (State) -> Int): String {
        fun List<Edge>.combineToRegex(): String {
            var result = ""
            val singleChars = mutableListOf<String>()
            for (edge in this) {
                if (edge.op.length == 1) {
                    // Only one character
                    singleChars.add(edge.op)
                } else if (edge.op.isNotEmpty()) {
                    result = if (result.isEmpty()) edge.op else "$result|${edge.op}"
                }
            }
            if (singleChars.size > 1) {
                result += "[" + singleChars.joinToString("") + "]"
            } else {
                result += singleChars.joinToString("")
            }

            return if ("|" in result) "($result)" else result
        }
        fun getSelfLoopOfState(toReplace: State): String {
            // First, we get the loop(s) to the same node.
            var selfLoop =
                toReplace.outgoingEdges
                    .filter { it.nextState == toReplace && it.op != EPSILON }
                    .combineToRegex()
            // EPSILON wouldn't change anything here because we put the asterisk operator around it.
            // So, we just remove such an edge.
            // There's a loop, so we surround it with brackets and put the * operator
            if (selfLoop.isEmpty()) return selfLoop
            selfLoop =
                if (selfLoop.length > 1 && !(selfLoop.startsWith("(") && selfLoop.endsWith(")")))
                    "($selfLoop)*"
                else "$selfLoop*"

            return selfLoop
        }

        fun getOutgoingEdgesMap(toReplace: State): Map<State, String> {
            val result = mutableMapOf<State, String>()
            // How can we reach the respective nodes?
            for ((k, v) in toReplace.outgoingEdges.groupBy { it.nextState }) {
                // Only consider edges to other nodes.
                if (k == toReplace) continue

                // Collect the different branches in one regex
                var regex = v.filter { it.op != EPSILON }.combineToRegex()

                // We put the ? around this regex because we can bypass it with the EPSILON edge.
                if (regex.isNotEmpty() && v.any { it.op == EPSILON }) regex = toOptional(regex)

                result[k] = regex
            }
            return result
        }

        fun replaceStateWithRegex(toReplace: State, remainingStates: MutableSet<State>) {
            val selfLoop = getSelfLoopOfState(toReplace)
            // We add the self-loop string to the front because it affects every single outgoing
            // edge.
            val outgoingMap = getOutgoingEdgesMap(toReplace).mapValues { (_, v) -> selfLoop + v }
            // Iterate over all states and their edges which have a transition to toReplace.
            // We replace this edge with edges to all nodes in outgoingMap and assemble the
            // respective string
            for (state in remainingStates) {
                val newEdges = mutableSetOf<Edge>()
                // Get the regex from state to the state to replace. There might be multiple options
                val outgoingEdges = state.outgoingEdges.filter { it.nextState == toReplace }
                var regexToReplace = outgoingEdges.filter { it.op != EPSILON }.combineToRegex()

                // If there's an EPSILON edge from state to toReplace, everything is optional
                if (outgoingEdges.any { it.op == EPSILON && regexToReplace.isNotEmpty() })
                    regexToReplace = toOptional(regexToReplace)

                // We add edges from this state to each state reachable from the one to remove.
                // It's the string to reach the state to be removed + the option(s) to reach the
                // next hop
                if (outgoingEdges.isNotEmpty()) {
                    for ((key, value) in outgoingMap.entries) {
                        newEdges.add(Edge(regexToReplace + value, null, key))
                    }
                }

                // We also need all the edges to other states
                newEdges.addAll(state.outgoingEdges.filter { it.nextState != toReplace })

                state.outgoingEdges = newEdges
            }
        }

        val copy = this.deepCopy() as NFA
        val (newStartState, newEndState) = copy.GNFAStartAndEndState()
        val stateSet = copy.states.toMutableSet()

        while (stateSet.isNotEmpty()) {
            val toProcess =
                stateSet
                    .filter { it != newStartState && it != newEndState }
                    .minByOrNull { heuristic(it) }
                    ?: return newStartState.outgoingEdges.joinToString("|") { "(${it.op})" }

            stateSet.remove(toProcess)
            replaceStateWithRegex(toProcess, stateSet)
        }

        return newStartState.outgoingEdges.joinToString("|") { "(${it.op})" }
    }

    /**
     * If the NFA already is a GNFA, this method returns the start and end state. Otherwise, it
     * generates a new start and end state,transforms the NFA accordingly and returns them.
     */
    private fun GNFAStartAndEndState(): Pair<State, State> {
        var returnedStart = this.states.first { it.isStart }
        var returnedEnd = this.states.first { it.isAcceptingState }

        if (this.needsNewStart()) {

            // We generate a new start state to make the termination a bit easier.
            val oldStart = this.states.first { it.isStart }
            oldStart.isStart = false
            val newStartState = this.addState(true, false)
            newStartState.addEdge(Edge(EPSILON, null, oldStart))
            returnedStart = newStartState
        }

        if (this.needsNewEnd()) {
            // We also generate a new end state
            val newEndState = this.addState(false, true)
            this.states
                .filter { it.isAcceptingState && it != newEndState }
                .forEach {
                    it.addEdge(Edge(EPSILON, null, newEndState))
                    it.isAcceptingState = false
                }
            returnedEnd = newEndState
        }
        return returnedStart to returnedEnd
    }

    private fun needsNewStart(): Boolean {
        val currentStart = this.states.first { it.isStart }
        // If the start state is an accepting state or has transitions into it, we need a new start
        return currentStart.isAcceptingState ||
            states.any { it.outgoingEdges.any { e -> e.nextState == currentStart } }
    }

    private fun needsNewEnd(): Boolean {
        val currentEnd = this.states.singleOrNull { it.isAcceptingState }
        // If there are multiple accepting sates or the accepting state has transitions out of it,
        // we need a new one
        return currentEnd == null || currentEnd.outgoingEdges.isNotEmpty()
    }
}
