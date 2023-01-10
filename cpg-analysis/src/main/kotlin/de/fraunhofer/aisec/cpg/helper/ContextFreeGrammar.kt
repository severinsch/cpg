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

import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.types.Type

abstract class Production

// A -> "abc"
class TerminalProduction(val terminal: Terminal) : Production() {
    // constructor(string_literal: String) : this(Regex.fromLiteral(string_literal))
}

// X -> Y
class UnitProduction(var y_id: Long) : Production()

// X -> op(Y)
class UnaryOpProduction(
    var op: Operation,
    var y_id: Long,
    var other_args: List<Long> = emptyList()
) : Production()

// X -> op(Y, Z)
class BinaryOpProduction(
    var op: Operation,
    var y_id: Long,
    var z_id: Long,
    var other_args: List<Long> = emptyList()
) : Production()

// X -> Y Z
class ConcatProduction(var y_id: Long, var z_id: Long) : Production()

class Nonterminal(var id: Long, val productions: MutableSet<Production> = mutableSetOf()) {
    fun addProduction(production: Production) {
        productions.add(production)
    }
}

class Terminal(val regex: Regex, val charset: CharSet) {

    companion object {
        fun anything(): Terminal {
            return Terminal(Regex(".*"), CharSet.sigma())
        }
    }

    constructor(type: Type) : this(getRegexForNodeType(type), getCharsetForNodeType(type))

    constructor(
        value: Any
    ) : this(
        Regex.fromLiteral(value.toString()),
        SetCharSet(value.toString().toCollection(mutableSetOf()))
    )
}

class ContextFreeGrammar(var nonterminals: HashMap<Long, Nonterminal> = hashMapOf()) {

    fun clear() {
        nonterminals.clear()
    }

    fun addNonterminal(node: Node, nt: Nonterminal) {
        nonterminals[node.id!!] = nt
    }

    fun addNonterminal(id: Long, nt: Nonterminal) {
        nonterminals[id] = nt
    }

    fun getNonterminal(node: Node): Nonterminal? {
        return nonterminals[node.id!!]
    }

    fun getNonterminal(id: Long): Nonterminal? {
        return nonterminals[id]
    }

    fun getSuccessorsFor(nt: Nonterminal): Iterable<Nonterminal> {
        return nt.productions.flatMap { p ->
            when (p) {
                is TerminalProduction -> emptyList()
                is UnaryOpProduction -> listOf(this.getNonterminal(p.y_id)!!)
                is BinaryOpProduction ->
                    listOf(this.getNonterminal(p.y_id)!!, this.getNonterminal(p.z_id)!!)
                is UnitProduction -> listOf(this.getNonterminal(p.y_id)!!)
                is ConcatProduction ->
                    listOf(this.getNonterminal(p.y_id)!!, this.getNonterminal(p.z_id)!!)
                else -> throw IllegalStateException("unreachable when branch")
            }
        }
    }

    fun getPredecessors(): MutableMap<Nonterminal, MutableSet<Nonterminal>> {
        val predecessors = mutableMapOf<Nonterminal, MutableSet<Nonterminal>>()
        for (nt in nonterminals.values) {
            for (prod in nt.productions) {
                when (prod) {
                    is TerminalProduction -> {}
                    is UnitProduction -> {
                        // for nt -> B adds nt to predecessors[B]
                        predecessors.compute(nonterminals[prod.y_id]!!) { _, preds ->
                            preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                        }
                    }
                    is UnaryOpProduction -> {
                        // for nt -> op(B) adds nt to predecessors[B]
                        predecessors.compute(nonterminals[prod.y_id]!!) { _, preds ->
                            preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                        }
                    }
                    is BinaryOpProduction -> {
                        // for nt -> op(X, Y) adds nt to predecessors[X] and predecessors[Y]
                        for (x in listOfNotNull(nonterminals[prod.y_id], nonterminals[prod.z_id])) {
                            predecessors.compute(x) { _, preds ->
                                preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                            }
                        }
                    }
                    is ConcatProduction -> {
                        // for nt -> X Y adds nt to predecessors[X] and predecessors[Y]
                        for (x in listOfNotNull(nonterminals[prod.y_id], nonterminals[prod.z_id])) {
                            predecessors.compute(x) { _, preds ->
                                preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                            }
                        }
                    }
                    else -> throw IllegalStateException("unreachable when branch")
                }
            }
        }
        return predecessors
    }

    fun printGrammar(): String {
        return nonterminals.entries.joinToString(separator = "\n") { (_, nt) ->
            nt.productions.joinToString(separator = "\n") { p ->
                "${nt.id} -> " +
                    when (p) {
                        is TerminalProduction -> "\"${p.terminal.regex}\""
                        is UnitProduction -> p.y_id
                        is UnaryOpProduction -> "${p.op}(${p.y_id})"
                        is BinaryOpProduction -> "${p.op}(${p.y_id}, ${p.z_id})"
                        is ConcatProduction -> "${p.y_id} ${p.z_id}"
                        else -> "error"
                    }
            }
        }
    }
}
