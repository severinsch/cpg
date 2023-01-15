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

import de.fraunhofer.aisec.cpg.graph.types.Type
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet
import de.fraunhofer.aisec.cpg.helper.approximations.SetCharSet
import kotlin.math.max

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

/** A production with one target [Nonterminal] on the right hand side. */
sealed interface UnaryProduction : Production {
    val target1: Nonterminal
}

/** A production with two target [Nonterminal]s on the right hand side. */
sealed interface BinaryProduction : Production {
    val target1: Nonterminal
    val target2: Nonterminal
}

/**
 * A production with an associated [Operation] that is applied to the [Nonterminal](s) on the right
 * hand side.
 */
sealed interface OperationProduction : Production {
    val op: Operation
}

/** A production of type X -> [Terminal] */
class TerminalProduction(val terminal: Terminal) : Production {
    // constructor(string_literal: String) : this(Regex.fromLiteral(string_literal))
}

/** A production of type X -> Y. */
class UnitProduction(override val target1: Nonterminal) : UnaryProduction

/** A production of type X -> op(Y). */
class UnaryOpProduction(
    override val op: Operation,
    override val target1: Nonterminal,
    var other_args: List<Long> = emptyList(),
) : OperationProduction, UnaryProduction

/** A production of type X -> op(Y, Z). */
class BinaryOpProduction(
    override val op: Operation,
    override val target1: Nonterminal,
    override val target2: Nonterminal,
    var other_args: List<Long> = emptyList()
) : OperationProduction, BinaryProduction

/** A production of type X -> Y Z. */
class ConcatProduction(override val target1: Nonterminal, override val target2: Nonterminal) :
    BinaryProduction

class Nonterminal(val id: Long, val productions: MutableSet<Production> = mutableSetOf()) :
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

    fun replaceProductions(newProds: MutableSet<Production>) {
        productions.clear()
        productions.addAll(newProds)
    }
}

class Terminal(val regex: Regex, val charset: CharSet) {

    companion object {
        fun anything(): Terminal {
            return Terminal(Regex(".*"), CharSet.sigma())
        }

        fun epsilon(): Terminal {
            return Terminal(Regex(""), CharSet.empty())
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

class ContextFreeGrammar(private val nonterminals: HashMap<Long, Nonterminal> = hashMapOf()) {
    private var maxId = nonterminals.keys.maxOrNull() ?: -1

    fun clear() {
        nonterminals.clear()
    }

    fun getAllNonterminals(): MutableCollection<Nonterminal> {
        return nonterminals.values
    }

    /**
     * Adds the given [Nonterminal] to the grammar if there is no existing [Nonterminal] with the
     * same id.
     */
    fun addNonterminal(nt: Nonterminal) {
        maxId = max(maxId, nt.id)
        nonterminals.putIfAbsent(nt.id, nt)
    }

    /**
     * Returns the [Nonterminal] associated with the passed [id] if it exists. If it doesn't exist,
     * a new [Nonterminal] with the given [id] is added to the grammar and returned.
     * @param id Assumes id is not null to limit the amount of non-null assertions when calling the
     * function
     * @return the existing [Nonterminal] or the newly created [Nonterminal]
     */
    fun getOrCreateNonterminal(id: Long?): Nonterminal {
        maxId = max(maxId, id!!)
        // if id is not present, adds new Nonterminal and returns it
        // if id is present returns value
        return nonterminals.computeIfAbsent(id) { k -> Nonterminal(k) }
    }

    /**
     * Finds the successors for the given [Nonterminal].
     * @return an [Iterable] containg all successors of [nt]
     */
    fun getSuccessorsFor(nt: Nonterminal): Iterable<Nonterminal> {
        return nt.productions.flatMap { p ->
            when (p) {
                is TerminalProduction -> emptyList()
                is UnaryProduction -> listOf(p.target1)
                is BinaryProduction -> listOf(p.target1, p.target2)
            }
        }
    }

    /**
     * Finds the predecessors for each [Nonterminal] in the grammar.
     * @return a mapping from each [Nonterminal] to its predecessors
     */
    fun getAllPredecessors(): Map<Nonterminal, Set<Nonterminal>> {
        val predecessors = mutableMapOf<Nonterminal, MutableSet<Nonterminal>>()
        for (nt in nonterminals.values) {
            for (prod in nt.productions) {
                when (prod) {
                    is UnaryProduction -> {
                        // for nt -> B, nt -> op(B) adds nt to predecessors[B]
                        predecessors.computeIfAbsent(prod.target1) { mutableSetOf() }.add(nt)
                    }
                    is BinaryProduction -> {
                        // for nt -> X Y, nt -> op(X, Y) adds nt to predecessors[X] and
                        // predecessors[Y]
                        for (x in listOfNotNull(prod.target1, prod.target2)) {
                            predecessors.computeIfAbsent(x) { mutableSetOf() }.add(nt)
                        }
                    }
                    is TerminalProduction -> {}
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
            comp.nonterminals.joinToString(separator = "; ") {
                "node_${it.id}"
            }
        }}"
            }
                ?: ""

        return "digraph grammar {\n$nodes$edges$sccSubgraphs\n}"
    }

    fun createNewNonterminal(): Nonterminal {
        maxId++
        return Nonterminal(maxId)
    }
}
