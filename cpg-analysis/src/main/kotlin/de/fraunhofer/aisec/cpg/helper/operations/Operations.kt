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
package de.fraunhofer.aisec.cpg.helper.operations

import de.fraunhofer.aisec.cpg.analysis.ValueEvaluator
import de.fraunhofer.aisec.cpg.analysis.fsm.NFA
import de.fraunhofer.aisec.cpg.analysis.fsm.State
import de.fraunhofer.aisec.cpg.graph.statements.expressions.BinaryOperator
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.helper.*
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet
import java.lang.IllegalStateException

fun createOperationProduction(node: CallExpression, cfg: Grammar): Production {
    if (node.base == null) {
        return TerminalProduction(Terminal.anything())
    }
    val baseNT = cfg.getOrCreateNonterminal(node.base?.id)

    val name = node.name.lowercase()

    if (node.isNumber()) {
       return getNumberProduction(node)
    }

    if (name in setOf("replace", "trim", "tolowercase", "touppercase")) {
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
                "trim" -> Trim()
                "tolowercase" -> ToLowerCase()
                "touppercase" -> ToUpperCase()
                else -> throw IllegalStateException("Unreachable")
            }
        return OperationProduction(operation, baseNT)
    }
    if (name in setOf("concat")) {
        val argNT = cfg.getOrCreateNonterminal(node.arguments.first().id)
        return ConcatProduction(baseNT, argNT)
    }
    if (name in setOf("repeat")) {
        return OperationProduction(Repeat(node, node.arguments.first()), baseNT)
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
            return OperationProduction(Repeat(node, amount), cfg.getOrCreateNonterminal(arg.id))
        }
    }
    return TerminalProduction(Terminal.anything())
}

sealed class Operation(val priority: Int) {
    // unknown operation, could add any characters -> Σ
    open fun charsetTransformation(cs: CharSet): CharSet = CharSet.sigma()
    open fun regularApproximation(automaton: NFA, affectedStates: List<State>): Unit = TODO()
    abstract override fun toString(): String
}
