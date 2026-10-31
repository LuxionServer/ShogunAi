package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleBuildsTest {

    private val root = "/home/dev/projects/main-repo"

    private fun folders(files: List<String>, directories: Set<String> = emptySet()) = gradleBuildFolders(
        FakeFileManager(existing = files.map { "$root/$it" }, directories = directories.map { "$root/$it" }.toSet()),
        root,
    )

    @Test
    fun `the main folder is the build when it has a wrapper or settings`() {
        assertEquals(listOf(""), folders(listOf("gradlew")))
        assertEquals(listOf(""), folders(listOf("settings.gradle")))
    }

    @Test
    fun `a direct subfolder with settings is the build when the main folder isn't one`() {
        val found = folders(
            listOf("pubspec.yaml", "android/settings.gradle.kts", "android/app/build.gradle.kts", "ios/Runner.xcodeproj"),
            setOf("android", "ios"),
        )

        assertEquals(listOf("android"), found)
    }

    @Test
    fun `a nested build next to a root build adds nothing`() {
        val found = folders(listOf("settings.gradle.kts", "build-logic/settings.gradle.kts"), setOf("build-logic"))

        assertEquals(listOf(""), found)
    }

    @Test
    fun `skips hidden, build, node_modules and Pods folders`() {
        val found = folders(
            listOf(".hidden/settings.gradle", "build/settings.gradle", "node_modules/settings.gradle", "Pods/settings.gradle"),
            setOf(".hidden", "build", "node_modules", "Pods"),
        )

        assertEquals(emptyList(), found)
    }
    @Test
    fun `a build in the main folder isn't nested`() {
        val fileManager = FakeFileManager(existing = listOf("$root/settings.gradle"))

        assertEquals(emptyList(), nestedGradleBuildFolders(fileManager, root))
    }
}
