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

import de.fraunhofer.aisec.cpg.graph.types.Type
import de.fraunhofer.aisec.cpg.helper.approximations.CharSet
import de.fraunhofer.aisec.cpg.helper.approximations.SetCharSet

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

sealed interface Symbol

class Nonterminal(
    val id: Long,
    val productions: MutableSet<Production> = mutableSetOf(),
    var label: String = id.toString()
) : Comparable<Nonterminal>, Symbol {
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

    override fun toString(): String {
        return label
    }
}

class Terminal(
    val value: String,
    val charset: CharSet,
    val isLiteral: Boolean = true,
    val isEpsilon: Boolean = false
) : Symbol {

    companion object {
        fun anything(): Terminal {
            return Terminal(".*", CharSet.sigma(), isLiteral = false)
        }

        fun epsilon(): Terminal {
            return Terminal("ε", CharSet.empty(), isLiteral = true, isEpsilon = true)
        }
    }

    constructor(
        type: Type
    ) : this(getRegexPatternForNodeType(type), getCharsetForNodeType(type), isLiteral = false)

    constructor(
        value: Any
    ) : this(
        value.toString(),
        SetCharSet(value.toString().toCollection(mutableSetOf())),
        isLiteral = true
    )
}
