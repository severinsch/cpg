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

import kotlin.test.*
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CharSetTest {
    private val letters = SetCharSet(('a'..'z').toMutableSet())
    private val lettersCheck = SetCharSet(('a'..'z').toMutableSet())
    private val digits = SetCharSet(('0'..'9').toMutableSet())
    private val sigma = CharSet.sigma()

    @Test
    fun `remove changes set if element is contained`() {
        // Σ \ {a} != Σ
        sigma.remove('a')
        assertNotEquals(CharSet.sigma(), sigma)

        // a \elem A => A \ {a} != A
        val member = letters.chars.random()
        letters.remove(member)
        assertNotEquals(lettersCheck, letters)
    }

    @Test
    fun `remove doesn't change set if element is not contained`() {
        // !a \elem A => A \ {a} == A
        val non_member = letters.chars.max() + 1
        letters.remove(non_member)
        assertEquals(lettersCheck, letters)
    }

    @Test
    fun `union works as expected`() {
        // Σ ∪ A == Σ
        val new = sigma union letters
        assertEquals(CharSet.sigma(), new)

        // a \elem A => Σ \ {a} ∪ A == Σ
        sigma.remove(letters.chars.random())
        assertEquals(CharSet.sigma(), sigma union letters)

        // definition of union
        (letters union digits).let { alphanum ->
            assert(letters.chars.all { alphanum.contains(it) })
            assert(digits.chars.all { alphanum.contains(it) })
        }

        // {} ∪ {} == {}
        assertEquals(CharSet.empty(), CharSet.empty() union CharSet.empty())
    }

    @Test
    fun `intersection works as expected`() {
        // intersection of disjunct sets is empty
        assertEquals(CharSet.empty(), letters intersect digits)

        // A ∩ {} == {}
        assertEquals(CharSet.empty(), letters intersect CharSet.empty())
    }

    @Test
    fun `intersection and union properties`() {
        // (A ∪ B) ∩ B == B
        assertEquals(digits, (letters union digits) intersect digits)
    }
}
