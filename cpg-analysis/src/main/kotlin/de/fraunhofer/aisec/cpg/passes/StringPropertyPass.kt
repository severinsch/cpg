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
package de.fraunhofer.aisec.cpg.passes

import de.fraunhofer.aisec.cpg.TranslationResult
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.ReturnStatement
import de.fraunhofer.aisec.cpg.graph.statements.expressions.*
import de.fraunhofer.aisec.cpg.helpers.SubgraphWalker
import de.fraunhofer.aisec.cpg.passes.order.DependsOn

object StringPropertyHotspots {
    enum class HotspotType {
        UNDETERMINED,
        PRINT,
        RETURN,
        DATABASE
    }

    val hotspots: MutableSet<Expression> = mutableSetOf()
    val print_hotspots: MutableSet<Expression> = mutableSetOf()
    val return_hotspots: MutableSet<Expression> = mutableSetOf()
    val database_hotspots: MutableSet<Expression> = mutableSetOf()
    fun addHotspot(node: Expression, type: HotspotType) {
        hotspots.add(node)
        when (type) {
            HotspotType.UNDETERMINED -> {}
            HotspotType.PRINT -> print_hotspots.add(node)
            HotspotType.RETURN -> return_hotspots.add(node)
            HotspotType.DATABASE -> database_hotspots.add(node)
        }
    }

    fun clear() {
        hotspots.clear()
        print_hotspots.clear()
        return_hotspots.clear()
        database_hotspots.clear()
    }
}

@DependsOn(DFGPass::class)
@DependsOn(EvaluationOrderGraphPass::class)
@DependsOn(IdentifierPass::class)
class StringPropertyPass : Pass() {

    override fun accept(result: TranslationResult) {

        val walker = SubgraphWalker.IterativeGraphWalker()
        walker.registerOnNodeVisit { node -> collectHotspots(node) }
        for (tu in result.translationUnits) {
            walker.iterate(tu)
        }
    }

    private fun collectHotspots(node: Node?) {
        when (node) {
            is ReturnStatement -> {
                val returnTypes =
                    (node.nextDFG.firstOrNull { it is FunctionDeclaration } as FunctionDeclaration?)
                        ?.returnTypes
                        ?: emptyList()
                val t = node.returnValue?.type
                val anyIsString =
                    returnTypes
                        .let { if (t != null) it + t else it }
                        .any { it.typeName == "java.lang.String" }
                if (node.returnValue != null && anyIsString) {
                    StringPropertyHotspots.addHotspot(
                        node.returnValue,
                        StringPropertyHotspots.HotspotType.RETURN
                    )
                }
            }
            is CallExpression -> {
                if (node.fqn?.contains(".println") == true && node.arguments.isNotEmpty()) {
                    StringPropertyHotspots.addHotspot(
                        node.arguments.first(),
                        StringPropertyHotspots.HotspotType.PRINT
                    )
                }
                if (
                    node.fqn?.matches(
                        Regex(
                            "java\\.sql\\.(?:(?:Callable|Prepared)?Statement\\.(execute.*|addBatch)|Connection\\.prepare.*)"
                        )
                    ) == true && node.arguments.isNotEmpty()
                ) {
                    StringPropertyHotspots.addHotspot(
                        node.arguments.first(),
                        StringPropertyHotspots.HotspotType.DATABASE
                    )
                }
            }
        }
    }

    override fun cleanup() {}
}
