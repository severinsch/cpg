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
package de.fraunhofer.aisec.cpg.helper

import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.ParamVariableDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.VariableDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.ReturnStatement
import de.fraunhofer.aisec.cpg.graph.statements.expressions.*
import de.fraunhofer.aisec.cpg.processing.IVisitor
import de.fraunhofer.aisec.cpg.processing.strategy.Strategy
import java.util.function.Predicate

fun makeStrategyConditional(
    strat: (n: Node) -> Iterator<Node>,
    pred: Predicate<Node>
): (n: Node) -> Iterator<Node> {
    return { node: Node ->
        if (pred.test(node)) strat.invoke(node) else emptyList<Node>().iterator()
    }
}

val DFG_BACKWARD_NO_NUMBERS =
    makeStrategyConditional(Strategy::DFG_BACKWARD) { n: Node -> !n.isNumber() }

fun createGrammar(node: Node): Grammar {
    val cfg = Grammar()
    node.accept(
        DFG_BACKWARD_NO_NUMBERS,
        object : IVisitor<Node>() {
            override fun visit(n: Node) {
                handle(n, cfg)
                super.visit(n)
            }
        }
    )
    return cfg
}

fun handle(node: Node?, cfg: Grammar) {
    println("${node?.id!!}: ${node.name} at ${node.location.toString()}")
    when (node) {
        is ArrayCreationExpression -> TODO()
        is ArraySubscriptionExpression -> TODO()
        is ExpressionList -> TODO()
        // these should be handled by the defaultHandler, double check to make sure
        // is ConditionalExpression -> TODO()
        // is MemberExpression -> TODO()
        // Expressions
        is ConstructExpression -> handleConstructExpression(node, cfg)
        is CallExpression -> handleCallExpression(node, cfg)
        is CastExpression -> handleDefault(node, cfg)
        is BinaryOperator -> handleBinaryOp(node, cfg)
        is DeclaredReferenceExpression -> handleDeclaredReferenceExpression(node, cfg)
        is VariableDeclaration -> handleVariableDeclaration(node, cfg)
        // Other
        // is Assignment -> handleAssignment(node)
        is ReturnStatement -> handleReturnStatement(node, cfg)
        is Literal<*> -> handleLiteral(node, cfg)
        is FunctionDeclaration -> handleFunctionDeclaration(node, cfg)
        is ParamVariableDeclaration -> handleParamVariableDeclaration(node, cfg)
        is NewExpression -> handleNewExpression(node, cfg)
        else -> handleDefault(node, cfg)
    }
}

private fun handleDefault(node: Node, cfg: Grammar) {
    println("default handled node ${node.javaClass.name} (id: ${node.id}) at ${node.location}")
    val nt = cfg.getOrCreateNonterminal(node.id!!)

    if (node.prevDFG.isEmpty()) {
        return
    }
    for (dataSource in node.prevDFG) {
        val dataSourceNT = cfg.getOrCreateNonterminal(dataSource.id)
        nt.addProduction(UnitProduction(dataSourceNT))
    }
    cfg.addNonterminal(nt)
}

private fun handleNewExpression(node: NewExpression, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)
    for (dataSource in node.prevDFG) {
        println("DATA SOURCE FOR NEW EXPRESSION: $dataSource")
    }
    val init = node.initializer // ConstructExpression for java
    if (init != null) {
        val initNT = cfg.getOrCreateNonterminal(init.id)
        nt.addProduction(UnitProduction(initNT))
    }
    cfg.addNonterminal(nt)
}

private fun handleConstructExpression(node: ConstructExpression, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)
    // Constructor call of Java Number Wrapper
    if (node.isNumber()) {
        // should be TerminalProduction with literal of Constructor Argument most of the time
        val prod = getNumberProduction(node.arguments[0])
        nt.addProduction(prod)
        cfg.addNonterminal(nt)
        return
    }
    if (node.isString()) {
        val args = node.arguments
        if (args.isEmpty()) {
            // String() == ""
            nt.addProduction(TerminalProduction(Terminal("")))
            cfg.addNonterminal(nt)
            return
        }
        if (args[0].isString()) {
            // String("original")
            val argsNT = cfg.getOrCreateNonterminal(args[0].id)
            nt.addProduction(UnitProduction(argsNT))
            cfg.addNonterminal(nt)
            return
        }
        // byte[]/char[] etc, complex with charsets, not handled currently
    }
    // here it would be nice to have some information about the toString() method of the object
    nt.addProduction(TerminalProduction(Terminal.anything()))
    cfg.addNonterminal(nt)
    return
}

private fun handleParamVariableDeclaration(node: ParamVariableDeclaration, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)
    if (node.prevDFG.isEmpty() || node.isNumber()) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else {
        for (dataSource in node.prevDFG) {
            val dataSourceNT = cfg.getOrCreateNonterminal(dataSource.id)
            nt.addProduction(UnitProduction(dataSourceNT))
        }
    }
    cfg.addNonterminal(nt)
}

private fun handleLiteral(node: Literal<*>, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)
    nt.addProduction(TerminalProduction(Terminal(node.value)))
    cfg.addNonterminal(nt)
}

private fun handleVariableDeclaration(node: VariableDeclaration, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)

    val initializer = node.initializer
    if (initializer == null || initializer is UninitializedValue) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else {
        val initializerNT = cfg.getOrCreateNonterminal(initializer.id)
        nt.addProduction(UnitProduction(initializerNT))
    }
    cfg.addNonterminal(nt)
}

private fun handleDeclaredReferenceExpression(node: DeclaredReferenceExpression, cfg: Grammar) {

    val nt = cfg.getOrCreateNonterminal(node.id!!)
    // TODO extend isNumber check (function calls that return int etc) and pull out
    if (node.prevDFG.isEmpty()) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else if (node.isNumber()) {
        val prod = getNumberProduction(node)
        nt.addProduction(prod)
    } else {
        for (dataSource in node.prevDFG) {
            val dataSourceNT = cfg.getOrCreateNonterminal(dataSource.id)
            nt.addProduction(UnitProduction(dataSourceNT))
        }
    }

    cfg.addNonterminal(nt)
}

private fun handleBinaryOp(node: BinaryOperator, cfg: Grammar) {
    if (node.isAssignment) {
        // handled by DFG edge from RHS to LHS
        return
    }
    val nt = cfg.getOrCreateNonterminal(node.id!!)

    nt.addProduction(createOperationProduction(node, cfg))
    // nt.addProduction(BinaryOpProduction(node.operatorCode, lhs.id!!, rhs.id!!))

    cfg.addNonterminal(nt)
}

private fun handleCallExpression(node: CallExpression, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)

    if (node.nextDFG.isEmpty()) {
        // e.g. for System.out.println(s) we don't want any productions
        return
    }
    // for local functions where invokes is known: get grammar info from function
    if (node.invokes.isNotEmpty()) {
        for (func in node.invokes) {
            val funcNT = cfg.getOrCreateNonterminal(func.id)
            nt.addProduction(UnitProduction(funcNT))
        }
    } else {
        nt.addProduction(createOperationProduction(node, cfg))
    }

    cfg.addNonterminal(nt)
}

private fun handleFunctionDeclaration(node: FunctionDeclaration, cfg: Grammar) {
    val nt = cfg.getOrCreateNonterminal(node.id!!)

    if (node.prevDFG.isEmpty()) {
        return
    }
    for (dataSource in node.prevDFG) {
        val dataSourceNT = cfg.getOrCreateNonterminal(dataSource.id)
        nt.addProduction(UnitProduction(dataSourceNT))
    }
    cfg.addNonterminal(nt)
}

private fun handleReturnStatement(node: ReturnStatement, cfg: Grammar) {
    if (node.returnValue != null) {
        val nt = cfg.getOrCreateNonterminal(node.id!!)
        val returnValueNT = cfg.getOrCreateNonterminal(node.returnValue.id)
        nt.addProduction(UnitProduction(returnValueNT))
        cfg.addNonterminal(nt)
    }
}
