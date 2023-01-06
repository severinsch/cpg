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
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.statements.expressions.Expression
import de.fraunhofer.aisec.cpg.graph.types.Type

fun Node?.isNumber(): Boolean {
    if (this is Expression) {
        return when (this.type.typeName) {
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Short",
            "java.lang.Byte",
            "byte",
            "short",
            "int",
            "long",
            "float",
            "double" -> true
            else -> false
        }
    }
    return false
}

fun Node?.isString(): Boolean {
    if (this is Expression) {
        return this.type.typeName == "java.lang.String"
    }
    return false
}

fun getRegexForType(type: Type): Regex {
    return when (type.typeName) {
        "java.lang.String" -> Regex(".*")
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Short",
        "java.lang.Byte",
        "byte",
        "short",
        "int",
        "long" -> Regex("0|(-?[1-9][0-9]*)")
        else -> Regex(".*")
    }
}

fun getNumberProduction(node: Expression): Production {
    val value = ValueEvaluator().evaluate(node)
    if (value is Number) {
        return RegexProduction(Regex.fromLiteral(value.toString()))
    }
    return RegexProduction(getRegexForType(node.type))
}
