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

import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet

class Repeat(val node: Node, val amount: Node) : Operation(1) {
    // STUB: this is a stub for the repeat operation, the automaton transformation is not yet
    // implemented
    // for things like this the automaton transformation will maybe try to use a ValueEvaluator to
    // get the Int value of amount and just concatenate the subautomaton for the base n times,
    // (base*)
    // otherwise

    constructor(repeatCall: CallExpression) : this(repeatCall, repeatCall.arguments[0])

    override fun charsetTransformation(cs: CharSet): CharSet = cs.copy()

    override fun toString(): String {
        return "repeat[${amount.id}]"
    }
}
