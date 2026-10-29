package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckGradleBuildCacheUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val gradleUserHome = "/home/dev/.gradle"

    private fun check(properties: Map<String, Map<String, String>>, files: List<String> = emptyList(), directories: Set<String> = emptySet()) =
        CheckGradleBuildCacheUseCase(FakeFileManager(existing = files, properties = properties, directories = directories), gradleUserHome)(repository)

    @Test
    fun `enabled in the repository`() {
        assertTrue(check(mapOf("$repository/gradle.properties" to mapOf("org.gradle.caching" to "true"))))
    }

    @Test
    fun `enabled in a nested build`() {
        assertTrue(
            check(
                mapOf("$repository/android/gradle.properties" to mapOf("org.gradle.caching" to "true")),
                files = listOf("$repository/android/settings.gradle.kts"),
                directories = setOf("$repository/android"),
            ),
        )
    }

    @Test
    fun `enabled for the user`() {
        assertTrue(check(mapOf("$gradleUserHome/gradle.properties" to mapOf("org.gradle.caching" to " TRUE "))))
    }

    @Test
    fun `off when neither file enables it`() {
        assertFalse(check(mapOf("$repository/gradle.properties" to mapOf("org.gradle.caching" to "false"))))
        assertFalse(check(emptyMap()))
    }
}
