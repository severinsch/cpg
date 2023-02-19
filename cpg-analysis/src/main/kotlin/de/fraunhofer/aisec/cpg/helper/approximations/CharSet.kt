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
package de.fraunhofer.aisec.cpg.helper.approximations

import kotlin.collections.Collection

sealed interface CharSet {
    companion object {
        fun sigma(): SigmaCharSet {
            return SigmaCharSet()
        }

        fun empty(): CharSet {
            return SetCharSet()
        }
    }

    operator fun contains(c: Char): Boolean
    infix fun union(other: CharSet): CharSet =
        when (other) {
            is SigmaCharSet -> union(other)
            is SetCharSet -> union(other)
        }
    infix fun union(other: SigmaCharSet): CharSet
    infix fun union(other: SetCharSet): CharSet
    fun union(others: Collection<CharSet>): CharSet {
        return others.fold(this) { acc: CharSet, cs -> acc.union(cs) }
    }
    infix fun intersect(other: CharSet): CharSet =
        when (other) {
            is SigmaCharSet -> intersect(other)
            is SetCharSet -> intersect(other)
        }
    infix fun intersect(other: SigmaCharSet): CharSet
    infix fun intersect(other: SetCharSet): CharSet
    fun add(c: Char)
    fun remove(c: Char)

    /** Returns a [Regex] that accepts the regular language C* for a [CharSet] C. */
    fun toRegexPattern(): String
}

/** A [CharSet] that represents a set of form C = Σ \ {c1, c2, c3, ...} */
class SigmaCharSet(val removed: MutableSet<Char> = mutableSetOf()) : CharSet {

    constructor(vararg cs: Char) : this(cs.toMutableSet())
    constructor(cs: Collection<Char>) : this(cs.toMutableSet())

    override operator fun contains(c: Char): Boolean {
        return !removed.contains(c)
    }

    // (Σ \ A) ∪ (Σ \ B) = Σ \ (A ∩ B)
    override infix fun union(other: SigmaCharSet): SigmaCharSet {
        return SigmaCharSet((this.removed intersect other.removed).toMutableSet())
    }

    // (Σ \ A) ∪ B = (Σ \ (A \ B))
    override infix fun union(other: SetCharSet): SigmaCharSet {
        return SigmaCharSet((removed - other.chars).toMutableSet())
    }

    // (Σ \ A) ∩ (Σ \ B) = Σ \ (A ∩ B) TODO check this
    override infix fun intersect(other: SigmaCharSet): SigmaCharSet {
        return SigmaCharSet((removed union other.removed).toMutableSet())
    }

    // (Σ \ A) ∩ B = B \ (A ∩ Σ) = B \ A TODO check this
    override infix fun intersect(other: SetCharSet): SetCharSet {
        return SetCharSet((other.chars - removed).toMutableSet())
    }

    override fun add(c: Char) {
        removed.remove(c)
    }

    override fun remove(c: Char) {
        removed.add(c)
    }

    override fun toRegexPattern(): String {
        if (removed.isEmpty()) {
            return ".*"
        }
        val chars = removed.joinToString(separator = "") { it.toString() }
        return "[^$chars]*"
    }

    override fun equals(other: Any?): Boolean {
        // SigmaCharSet and SetCharSet *could* be equal, this is ignored here
        return other is SigmaCharSet && other.removed == removed
    }

    override fun hashCode(): Int {
        // invert to ensure SigmaCharSet Σ \ {'a'} has a different hash than SetCharSet {'a'}.
        // like equals this doesn't account for SigmaCharSet and SetCharSet that are equal, since
        // this won't occur
        return removed.hashCode().inv()
    }

    override fun toString(): String {
        return "Σ \\ {${removed.joinToString(separator = ",", prefix = "{", postfix = "}") { it.toString() }}}"
    }
}

/** A [CharSet] that represents a set of form C = {c1, c2, c3, ...} */
class SetCharSet(val chars: MutableSet<Char> = mutableSetOf()) : CharSet {

    constructor(vararg cs: Char) : this(cs.toMutableSet())

    constructor(cs: Collection<Char>) : this(cs.toMutableSet())

    override operator fun contains(c: Char): Boolean {
        return chars.contains(c)
    }

    override infix fun union(other: SetCharSet): SetCharSet {
        return SetCharSet((chars union other.chars).toMutableSet())
    }

    override infix fun union(other: SigmaCharSet): SigmaCharSet {
        // use implementation in SigmaCharSet
        return other union this
    }

    override infix fun intersect(other: SetCharSet): SetCharSet {
        return SetCharSet((chars intersect other.chars).toMutableSet())
    }

    override infix fun intersect(other: SigmaCharSet): SetCharSet {
        // use implementation in SigmaCharSet
        return other intersect this
    }

    override fun add(c: Char) {
        chars.add(c)
    }

    override fun remove(c: Char) {
        chars.remove(c)
    }

    override fun toRegexPattern(): String {
        if (chars.isEmpty()) {
            return ""
        }
        return "[${chars.joinToString(separator = "")}]*"
    }

    override fun equals(other: Any?): Boolean {
        // SigmaCharSet and SetCharSet *could* be equal, this is ignored here
        return other is SetCharSet && other.chars == chars
    }

    override fun hashCode(): Int {
        return chars.hashCode()
    }

    override fun toString(): String {
        return chars.joinToString(separator = ",", prefix = "{", postfix = "}") { it.toString() }
    }
}
