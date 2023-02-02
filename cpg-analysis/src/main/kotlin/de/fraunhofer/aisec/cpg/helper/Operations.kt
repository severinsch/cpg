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

import de.fraunhofer.aisec.cpg.analysis.ValueEvaluator
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.statements.expressions.BinaryOperator
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet
import java.lang.IllegalStateException

fun createOperationProduction(node: CallExpression, cfg: Grammar): Production {
    if (node.base == null) {
        return TerminalProduction(Terminal.anything())
    }
    val baseNT = cfg.getOrCreateNonterminal(node.base?.id)

    val name = node.name.lowercase()
    if (name in setOf("replace", "trim")) {
        val operation =
            when (name) {
                "replace" -> {
                    val arg1 =
                        ValueEvaluator(cannotEvaluate = { _, _ -> null })
                            .evaluate(node.arguments[0])
                    val arg2 =
                        ValueEvaluator(cannotEvaluate = { _, _ -> null })
                            .evaluate(node.arguments[1])
                    if (arg1 == null && arg2 == null || arg1 !is Char && arg2 !is Char) {
                        ReplaceNoneKnown(node)
                    }
                    if (arg1 !is Char) {
                        ReplaceNewKnown(node.arguments[0], arg2 as Char)
                    }
                    if (arg2 !is Char) {
                        ReplaceOldKnown(arg1 as Char, node.arguments[1])
                    }
                    ReplaceBothKnown(arg1 as Char, arg2 as Char)
                }
                "trim" -> {
                    Trim(node)
                }
                else -> throw IllegalStateException("Unreachable")
            }
        return UnaryOpProduction(operation, baseNT)
    }
    if (name in setOf("concat")) {
        val argNT = cfg.getOrCreateNonterminal(node.arguments.first().id)
        return ConcatProduction(baseNT, argNT)
    }
    if (name in setOf("repeat")) {
        return UnaryOpProduction(Repeat(node, node.arguments.first()), baseNT)
    }
    return TerminalProduction(Terminal.anything())
}

fun createOperationProduction(node: BinaryOperator, cfg: Grammar): Production {
    when (node.operatorCode) {
        "+",
        "+=" -> {
            val lhsNT = cfg.getOrCreateNonterminal(node.lhs.id)
            val rhsNT = cfg.getOrCreateNonterminal(node.rhs.id)
            return ConcatProduction(lhsNT, rhsNT)
        }
        // not possible in Java, just to show that the Operations don't depend on whether they come
        // from a Call or an Operator
        "*" -> {
            val (amount, arg) =
                if (node.lhs?.type?.typeName?.lowercase()?.contains("int") == true)
                    arrayOf(node.lhs, node.rhs)
                else arrayOf(node.rhs, node.lhs)
            return UnaryOpProduction(Repeat(node, amount), cfg.getOrCreateNonterminal(arg.id))
        }
    }
    return TerminalProduction(Terminal.anything())
}

sealed class Operation(val priority: Int) {
    // STUBS, types will change
    open fun charsetTransformation(cs: CharSet): CharSet = cs
    // TODO maybe replace with subclasses for BinaryOperations
    open fun charsetTransformation(cs1: CharSet, cs2: CharSet): CharSet = cs1 union cs2
    open fun regularApproximation(automaton: NFA, affectedStates: List<State>): Unit = TODO()
    abstract override fun toString(): String
}

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

class Trim(trimCall: CallExpression) : Operation(1) {
    override fun toString(): String {
        return "trim"
    }

    override fun regularApproximation(automaton: NFA, affectedStates: List<State>) {
        // TODO
    }

    override fun charsetTransformation(cs: CharSet): CharSet {
        return cs
    }
}

class Repeat(val node: Node, val amount: Node) : Operation(1) {
    // for things like this the regular Approximation will maybe try to use a ValueEvaluator to
    // get the Int value of amount and just concatenate the regex for the base n times, (base*)
    // otherwise

    constructor(repeatCall: CallExpression) : this(repeatCall, repeatCall.arguments[0])
    override fun toString(): String {
        return "repeat[${amount.id}]"
    }
}
