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
import java.util.*
import java.util.function.Predicate

fun makeStrategyConditional(
    strat: (n: Node) -> Iterator<Node>,
    pred: Predicate<Node>
): (n: Node) -> Iterator<Node> {
    return { node: Node ->
        if (pred.test(node)) strat.invoke(node) else Collections.emptyIterator()
    }
}

val DFG_BACKWARD_COND = makeStrategyConditional(Strategy::DFG_BACKWARD) { n: Node -> !n.isNumber() }

fun createGrammar(node: Node): ContextFreeGrammar {
    val cfg = ContextFreeGrammar()
    node.accept(
        DFG_BACKWARD_COND,
        object : IVisitor<Node>() {
            override fun visit(n: Node) {
                handle(n, cfg)
                super.visit(n)
            }
        }
    )
    return cfg
}

fun handle(node: Node?, cfg: ContextFreeGrammar) {
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
        is UnaryOperator -> handleUnaryOperator(node, cfg)
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

private fun handleDefault(node: Node, cfg: ContextFreeGrammar) {
    println("default handled node ${node.javaClass.name} (id: ${node.id}) at ${node.location}")
    val nt = Nonterminal(node.id!!)

    if (node.prevDFG.isEmpty()) {
        return
    }
    for (dataSource in node.prevDFG) {
        nt.addProduction(UnitProduction(dataSource.id!!))
    }
    cfg.addNonterminal(node, nt)
}

private fun handleNewExpression(node: NewExpression, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)
    for (dataSource in node.prevDFG) {
        println("DATA SOURCE FOR NEW EXPRESSION: $dataSource")
    }
    val init = node.initializer // ConstructExpression for java
    if (init != null) {
        nt.addProduction(UnitProduction(init.id!!))
    }
    cfg.addNonterminal(node, nt)
}

private fun handleConstructExpression(node: ConstructExpression, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)
    // Constructor call of Java Number Wrapper
    if (node.isNumber()) {
        // should be TerminalProduction with literal of Constructor Argument most of the time
        val prod = getNumberProduction(node.arguments[0])
        nt.addProduction(prod)
        cfg.addNonterminal(node, nt)
        return
    }
    if (node.isString()) {
        val args = node.arguments
        if (args.isEmpty()) {
            // String() == ""
            nt.addProduction(TerminalProduction(Terminal("")))
            cfg.addNonterminal(node, nt)
            return
        }
        if (args[0].isString()) {
            // String("original")
            nt.addProduction(UnitProduction(args[0].id!!))
            cfg.addNonterminal(node, nt)
            return
        }
        // byte[]/char[] etc, complex with charsets, not handled currently
    }
    // here it would be nice to have some information about the toString() method of the object
    nt.addProduction(TerminalProduction(Terminal.anything()))
    cfg.addNonterminal(node, nt)
    return
}

private fun handleParamVariableDeclaration(
    node: ParamVariableDeclaration,
    cfg: ContextFreeGrammar
) {
    val nt = Nonterminal(node.id!!)
    if (node.prevDFG.isEmpty() || node.isNumber()) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else {
        for (data_source in node.prevDFG) {
            nt.addProduction(UnitProduction(data_source.id!!))
        }
    }
    cfg.addNonterminal(node, nt)
}

private fun handleLiteral(node: Literal<*>, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)
    nt.addProduction(TerminalProduction(Terminal(node.value)))
    cfg.addNonterminal(node, nt)
}

private fun handleVariableDeclaration(node: VariableDeclaration, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)

    val initializer = node.initializer
    if (initializer == null || initializer is UninitializedValue) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else {
        nt.addProduction(UnitProduction(initializer.id!!))
    }
    cfg.addNonterminal(node, nt)
}

private fun handleUnaryOperator(node: UnaryOperator, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)

    TODO()
    // nt.addProduction(UnaryOpProduction(node.operatorCode, node.input.id!!))

    cfg.addNonterminal(node, nt)
}

private fun handleDeclaredReferenceExpression(
    node: DeclaredReferenceExpression,
    cfg: ContextFreeGrammar
) {

    val nt = Nonterminal(node.id!!)
    // TODO extend isNumber check (function calls that return int etc) and pull out
    if (node.prevDFG.isEmpty()) {
        nt.addProduction(TerminalProduction(Terminal(node.type)))
    } else if (node.isNumber()) {
        val prod = getNumberProduction(node)
        nt.addProduction(prod)
    } else {
        for (data_source in node.prevDFG) {
            nt.addProduction(UnitProduction(data_source.id!!))
        }
    }

    cfg.addNonterminal(node, nt)
}

private fun handleBinaryOp(node: BinaryOperator, cfg: ContextFreeGrammar) {
    if (node.isAssignment) {
        // handled by DFG edge from RHS to LHS
        return
    }
    val nt = Nonterminal(node.id!!)

    nt.addProduction(createOperationProduction(node))
    // nt.addProduction(BinaryOpProduction(node.operatorCode, lhs.id!!, rhs.id!!))

    cfg.addNonterminal(node, nt)
}

private fun handleCallExpression(node: CallExpression, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)

    if (node.nextDFG.isEmpty()) {
        // e.g. for System.out.println(s) we don't want any productions
        return
    }
    // for local functions where invokes is known: get grammar info from function
    if (node.invokes.isNotEmpty()) {
        for (func in node.invokes) {
            nt.addProduction(UnitProduction(func.id!!))
        }
    } else {
        nt.addProduction(createOperationProduction(node))
    }

    cfg.addNonterminal(node, nt)
}

private fun handleFunctionDeclaration(node: FunctionDeclaration, cfg: ContextFreeGrammar) {
    val nt = Nonterminal(node.id!!)

    if (node.prevDFG.isEmpty()) {
        return
    }
    for (data_source in node.prevDFG) {
        nt.addProduction(UnitProduction(data_source.id!!))
    }
    cfg.addNonterminal(node, nt)
}

private fun handleReturnStatement(node: ReturnStatement, cfg: ContextFreeGrammar) {
    if (node.returnValue != null) {
        val nt = Nonterminal(node.id!!)
        nt.addProduction(UnitProduction(node.returnValue.id!!))
        cfg.addNonterminal(node, nt)
    }
}
