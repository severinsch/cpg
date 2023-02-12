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
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet

class ReplaceNoneKnown(val node: Node, val old: Node, val new: Node) : Operation(5) {
    constructor(
        replaceCall: CallExpression
    ) : this(replaceCall, replaceCall.arguments[0], replaceCall.arguments[1])

    override fun toString(): String {
        return "replace[<${old.id}>, <${new.id}>]"
    }
}

class ReplaceBothKnown(val old: Char, val new: Char) : Operation(4) {

    override fun charsetTransformation(cs: CharSet): CharSet {
        if (old in cs) {
            // TODO does this make a copy
            val newCS = cs
            newCS.remove(old)
            newCS.add(new)
            return newCS
        }
        return cs
    }

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        // TODO: handle case where old or new is regex special character
        affectedStates.forEach { state ->
            state.outgoingEdges =
                state.outgoingEdges
                    .map { edge ->
                        if (edge.taints.none { it.operation == this }) {
                            return@map edge
                        }
                        if (!edge.op.contains(old)) {
                            return@map edge
                        }
                        if (!edge.op.contains("\\Q")) {
                            return@map edge
                        }
                        return@map edge.copy(op = edge.op.replace(old, new))
                    }
                    .toSet()
        }
    }

    override fun toString(): String {
        return "replace[${old}, ${new}]"
    }
}

// TODO handle strings?
class ReplaceOldKnown(val old: Char, val new: Node) : Operation(3) {

    override fun charsetTransformation(cs: CharSet): CharSet {
        if (old in cs) {
            return CharSet.sigma()
        }
        return cs
    }

    override fun toString(): String {
        return "replace[${old}, <${new.id}>]"
    }
}

class ReplaceNewKnown(val old: Node, val new: Char) : Operation(2) {

    override fun charsetTransformation(cs: CharSet): CharSet {
        // TODO non pure problem?
        cs.add(new)
        return cs
    }

    override fun toString(): String {
        return "replace[<${old.id}>, ${new}>]"
    }
}
