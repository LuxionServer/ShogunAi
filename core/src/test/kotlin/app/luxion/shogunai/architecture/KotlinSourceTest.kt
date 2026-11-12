package app.luxion.shogunai.architecture

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KotlinSourceTest {

    private val text = """
        package app.luxion.shogunai.domain.usecase

        import app.luxion.shogunai.domain.model.Worktree
        import app.luxion.shogunai.infrastructure.NioFileManager as Files
        import app.luxion.shogunai.domain.io.*
        import kotlin.test.`Test`

        val a = app.luxion.shogunai.infrastructure.SystemClock()
        val b = "app.luxion.shogunai.infrastructure.InString"
    """.trimIndent() + "\n"

    private val source = KotlinSource("core/src/main/kotlin/A.kt", "core", "main", text)

    @Test
    fun `package and imports are read with their lines, aliases and wildcards`() {
        assertEquals("app.luxion.shogunai.domain.usecase", source.packageName)
        assertEquals(
            listOf(
                SourceReference("app.luxion.shogunai.domain.model.Worktree", 3),
                SourceReference("app.luxion.shogunai.infrastructure.NioFileManager", 4),
                SourceReference("app.luxion.shogunai.domain.io.*", 5),
                SourceReference("kotlin.test.Test", 6),
            ),
            source.imports,
        )
    }

    @Test
    fun `project references include qualified names in code but not in strings`() {
        assertEquals(
            listOf(
                "app.luxion.shogunai.domain.model.Worktree",
                "app.luxion.shogunai.infrastructure.NioFileManager",
                "app.luxion.shogunai.domain.io.*",
                "app.luxion.shogunai.infrastructure.SystemClock",
            ),
            source.projectReferences.map { it.target },
        )
        assertEquals(8, source.projectReferences.last().line)
    }

    @Test
    fun `file name, production flag, line count and location`() {
        assertEquals("A.kt", source.fileName)
        assertTrue(source.isProduction)
        assertEquals(9, source.lineCount)
        assertEquals(2, KotlinSource("b.kt", "core", "test", "a\nb").lineCount)
        assertEquals("", KotlinSource("c.kt", "core", "test", "val a = 1\n").packageName)
        assertEquals("core/src/main/kotlin/A.kt:3", source.location(3))
    }

    @Test
    fun `the tree reads every source set and the gradle scripts`() {
        val root = Files.createTempDirectory("tree")
        root.resolve("build.gradle.kts").writeText("plugins {}\n")
        root.resolve("notes.txt").writeText("x\n")
        root.resolve("core/src/main/kotlin/app").createDirectories().resolve("A.kt").writeText("package app\n")
        root.resolve("core/src/test/kotlin").createDirectories().resolve("ATest.kt").writeText("package app\n")
        root.resolve("core/src/test/kotlin/data.json").writeText("{}\n")
        root.resolve("core/build.gradle.kts").writeText("plugins {}\n")

        val tree = SourceTree.load(root, listOf("core"))

        assertEquals(listOf("core/src/main/kotlin/app/A.kt" to "main", "core/src/test/kotlin/ATest.kt" to "test"), tree.kotlinSources.map { it.path to it.sourceSet })
        assertEquals(listOf("build.gradle.kts" to "root", "core/build.gradle.kts" to "core"), tree.gradleScripts.map { it.path to it.module })
        assertEquals(listOf("core/src/main/kotlin/app/A.kt"), tree.production("core").map { it.path })
        assertEquals(4, tree.allSources.size)
    }

    @Test
    fun `a missing module fails instead of checking nothing`() {
        val root = Files.createTempDirectory("tree")
        assertFailsWith<IllegalArgumentException> { SourceTree.load(root, listOf("core")) }
    }
}
