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

/*
    this interface structure groups productions in groups to allow the following features

    // exhaustive but still concise, focus on "how many NTs are there on the left hand side" without needing more detail
    // for this to work, the interfaces need to be sealed
    when(production) {
        is TerminalProduction -> ...
        is UnaryProduction -> <... using target1>
        is BinaryProduction -> <... using target1 and target2>
    }

    // easy access to only operation productions, easy to ignore rest
    when(production) {
        is OperationProduction -> <do something with op>
        else -> <do nothing>
    }
*/

sealed interface Production

sealed interface UnaryProduction : Production {
    val target1: Node
}

sealed interface BinaryProduction : Production {
    val target1: Node
    val target2: Node
}

sealed interface OperationProduction : Production {
    val op: Operation
}

// A -> "abc"
class TerminalProduction(val terminal: Terminal) : Production {
    // constructor(string_literal: String) : this(Regex.fromLiteral(string_literal))
}

// X -> Y
class UnitProduction(override val target1: Node) : UnaryProduction

// X -> op(Y)
class UnaryOpProduction(
    override val op: Operation,
    override val target1: Node,
    var other_args: List<Long> = emptyList(),
) : OperationProduction, UnaryProduction

// X -> op(Y, Z)
class BinaryOpProduction(
    override val op: Operation,
    override val target1: Node,
    override val target2: Node,
    var other_args: List<Long> = emptyList()
) : OperationProduction, BinaryProduction

// X -> Y Z
class ConcatProduction(override val target1: Node, override val target2: Node) : BinaryProduction

class Nonterminal(var id: Long, val productions: MutableSet<Production> = mutableSetOf()) :
    Comparable<Nonterminal> {
    fun addProduction(production: Production) {
        productions.add(production)
    }

    override fun compareTo(other: Nonterminal): Int {
        return this.id.compareTo(other.id)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Nonterminal && this.id == other.id
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
                is UnaryProduction -> listOf(this.getNonterminal(p.target1)!!)
                is BinaryProduction -> listOf(this.getNonterminal(p.target1)!!, this.getNonterminal(p.target2)!!)
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
                        predecessors.compute(nonterminals[prod.target1.id]!!) { _, preds ->
                            preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                        }
                    }
                    is UnaryOpProduction -> {
                        // for nt -> op(B) adds nt to predecessors[B]
                        predecessors.compute(nonterminals[prod.target1.id]!!) { _, preds ->
                            preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                        }
                    }
                    is BinaryOpProduction -> {
                        // for nt -> op(X, Y) adds nt to predecessors[X] and predecessors[Y]
                        for (x in
                            listOfNotNull(
                                nonterminals[prod.target1.id],
                                nonterminals[prod.target2.id]
                            )) {
                            predecessors.compute(x) { _, preds ->
                                preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                            }
                        }
                    }
                    is ConcatProduction -> {
                        // for nt -> X Y adds nt to predecessors[X] and predecessors[Y]
                        for (x in
                            listOfNotNull(
                                nonterminals[prod.target1.id],
                                nonterminals[prod.target2.id]
                            )) {
                            predecessors.compute(x) { _, preds ->
                                preds?.plus(nt)?.toMutableSet() ?: mutableSetOf(nt)
                            }
                        }
                    }
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
                        is UnitProduction -> p.target1.id
                        is UnaryOpProduction -> "${p.op}(${p.target1.id})"
                        is BinaryOpProduction -> "${p.op}(${p.target1.id}, ${p.target2.id})"
                        is ConcatProduction -> "${p.target1.id} ${p.target2.id}"
                    }
            }
        }
    }

    fun toDOT(scc: SCC? = null): String {
        val nodes =
            nonterminals.values.joinToString(separator = "\n", postfix = "\n") { nt ->
                "node_${nt.id} [label = \"${nt.id}\"];"
            }
        val edges =
            nonterminals.values.joinToString(separator = "\n", postfix = "\n") { nt ->
                nt.productions.joinToString(separator = "\n") { prod ->
                    when (prod) {
                        is BinaryProduction ->
                            "node_${nt.id} -> node_${prod.target1.id};\n" +
                                "node_${nt.id} -> node_${prod.target2.id};"
                        is TerminalProduction ->
                            "node_${nt.id} -> \"regex${prod.hashCode()}_${prod.terminal.regex}\";"
                        is UnaryProduction -> "node_${nt.id} -> node_${prod.target1.id};"
                    }
                }
            }
        val sccSubgraphs =
            scc?.components?.joinToString(separator = "\n") { comp ->
                "subgraph cluster_${comp.hashCode()}{${
            comp.nonterminal.joinToString(separator = "; ") {
                "node_${it.id}"
            }
        }}"
            }
                ?: ""

        return "digraph grammar {\n$nodes$edges$sccSubgraphs\n}"
    }
}
