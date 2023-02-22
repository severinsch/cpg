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

    override fun charsetTransformation(cs: CharSet): CharSet {
        return CharSet.sigma()
    }
}

class ReplaceBothKnown(val old: Char, val new: Char) : Operation(4) {

    override fun charsetTransformation(cs: CharSet): CharSet {
        if (old in cs) {
            val newCS = cs.copy()
            newCS.remove(old)
            newCS.add(new)
            return newCS
        }
        return cs
    }

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        affectedStates.forEach { state ->
            state.outgoingEdges =
                state.outgoingEdges
                    .map { edge ->
                        if (edge.taints.none { it.operation == this }) {
                            return@map edge
                        }
                        if (!edge.op.contains("\\Q")) {
                            return@map handleRegex(edge)
                        }
                        if (!edge.op.contains(old)) {
                            return@map edge
                        }
                        return@map edge.copy(op = edge.op.replace(old, new))
                    }
                    .toSet()
        }
    }

    private fun handleRegex(edge: Edge): Edge {
        // TODO this is not very good, a better way would be converting the regex to some format
        // that's easier to manipulate (e.g. automaton)
        // this also doesn't handle all cases, e.g. character classes with ranges are not handled
        // correctly
        // however, as currently all regular expressions are created by our code, this should be
        // fine for now
        var op = edge.op
        val positiveCharClassRegex = Regex("([^\\\\]|^)\\[([^]^]*)]")
        // '-', '[' and ']' are special characters in character classes, so we need to escape them
        val escapedOld = if (old in listOf('-', '[', ']')) "\\$old" else old.toString()
        val escapedNew = if (new in listOf('-', '[', ']')) "\\$new" else new.toString()

        // handle character classes
        op =
            op.replace(positiveCharClassRegex) {
                val (before, content) = it.destructured
                return@replace "$before[${content.replace(escapedOld, escapedNew).replace(old.toString(), escapedNew)}]"
            }

        // handle negative character classes
        val negativeCharClassRegex = Regex("([^\\\\]|^)\\[(\\^[^]]*)]")
        op =
            op.replace(negativeCharClassRegex) {
                // "[^aby].replace('x', 'y')" -> "[^abx]"
                // "[^abx].replace('x', 'y')" -> "[^abx]"
                // "[^ab].replace('x', 'y')" -> "[^abx]"
                // "[^abxy].replace('x', 'y')" -> "[^abxy]"
                var (before, content) = it.destructured
                if (!(content.contains(new) && content.contains(old))) {
                    // all cases except the last one
                    content = content.replace(escapedNew, "").replace(new.toString(), "")
                }
                content += escapedOld

                return@replace "$before[$content]"
            }

        // handle wildcards
        val wildcardRegex = Regex("([^\\\\]|^)(\\.)")
        op =
            op.replace(wildcardRegex) {
                val (before, _) = it.destructured
                return@replace "$before[^${escapedOld}]"
            }

        return edge.copy(op = op)
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
        return cs.copy()
    }

    override fun toString(): String {
        return "replace[${old}, <${new.id}>]"
    }
}

class ReplaceNewKnown(val old: Node, val new: Char) : Operation(2) {

    override fun charsetTransformation(cs: CharSet): CharSet {
        // TODO non pure problem?
        val newCs = cs.copy()
        newCs.add(new)
        return newCs
    }

    override fun toString(): String {
        return "replace[<${old.id}>, ${new}>]"
    }
}
