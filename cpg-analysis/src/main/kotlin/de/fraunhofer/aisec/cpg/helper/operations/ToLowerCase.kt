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
import de.fraunhofer.aisec.cpg.helper.approximations.SetCharSet
import de.fraunhofer.aisec.cpg.helper.approximations.SigmaCharSet

class ToLowerCase : Operation(2) {

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        affectedStates.forEach { state ->
            state.outgoingEdges =
                state.outgoingEdges
                    .map { edge ->
                        if (edge.taints.none { it.operation == this }) {
                            return@map edge
                        }
                        if (!edge.op.contains("\\Q")) {
                            return@map edge
                        }
                        return@map edge.copy(op = edge.op.lowercase())
                    }
                    .toSet()
        }
    }

    override fun charsetTransformation(cs: CharSet): CharSet {
        return when (cs) {
            is SetCharSet -> SetCharSet(cs.chars.flatMap { it.lowercase().asSequence() })
            is SigmaCharSet -> {
                // This does not remove all possible upper case characters, but A to Z are the most
                // common
                SigmaCharSet((cs.removed + ('A'..'Z')))
            }
        }
    }

    override fun toString() = "toLowerCase"
}
