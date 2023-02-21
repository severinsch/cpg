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

import de.fraunhofer.aisec.cpg.TestUtils
import de.fraunhofer.aisec.cpg.TranslationManager
import de.fraunhofer.aisec.cpg.TranslationResult
import de.fraunhofer.aisec.cpg.analysis.StringPropertyEvaluator
import de.fraunhofer.aisec.cpg.frontends.java.JavaLanguage
import de.fraunhofer.aisec.cpg.passes.EdgeCachePass
import de.fraunhofer.aisec.cpg.passes.IdentifierPass
import de.fraunhofer.aisec.cpg.passes.StringPropertyHotspots
import de.fraunhofer.aisec.cpg.passes.StringPropertyPass
import java.nio.file.Path
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExampleTest {
    private lateinit var tr: TranslationResult

    @BeforeAll
    fun beforeAll() {
        val topLevel = Path.of("src", "test", "resources", "string_properties_benchmarks")
        TranslationManager.builder().build().analyze()
        tr =
            TestUtils.analyze(
                listOf(
                    topLevel.resolve("DatabaseSanitization.java").toFile(),
                ),
                topLevel,
                true
            ) {
                it.registerLanguage<JavaLanguage>()
                    .registerPass(IdentifierPass())
                    .registerPass(EdgeCachePass())
                    .registerPass(StringPropertyPass())
            }
    }

    @Test
    fun example() {
        println(StringPropertyHotspots.hotspots)
        val hotspot = StringPropertyHotspots.database_hotspots.first()

        val result = StringPropertyEvaluator().getRegexForNode(hotspot)
        println(prettyPrintPattern(result.pattern))
        println(result.pattern)
    }
}
