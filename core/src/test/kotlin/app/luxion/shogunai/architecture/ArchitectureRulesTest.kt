package app.luxion.shogunai.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureRulesTest {

    private val domain = DOMAIN_SOURCES

    private fun main(path: String, text: String): KotlinSource =
        KotlinSource(path, path.substringBefore('/'), "main", text)

    private fun tree(vararg sources: KotlinSource): SourceTree = SourceTree(sources.toList(), emptyList())

    private fun core(path: String, packageName: String, vararg lines: String): KotlinSource =
        main("core/src/main/kotlin/app/luxion/shogunai/$path", (listOf("package $packageName", "") + lines).joinToString("\n"))

    @Test
    fun `a model importing a use case breaks the layers`() {
        val model = core("domain/model/A.kt", "app.luxion.shogunai.domain.model", "import app.luxion.shogunai.domain.usecase.sameDirectory")
        assertEquals(
            listOf("$domain/model/A.kt:3: app.luxion.shogunai.domain.model must not depend on app.luxion.shogunai.domain.usecase (app.luxion.shogunai.domain.usecase.sameDirectory)"),
            CoreLayers.violations(tree(model)),
        )
    }

    @Test
    fun `ports, adapters and use cases keep to their layers`() {
        val port = core("domain/io/P.kt", "app.luxion.shogunai.domain.io", "import app.luxion.shogunai.domain.usecase.WorktreeGroup", "import app.luxion.shogunai.domain.model.Worktree")
        val adapter = core("infrastructure/I.kt", "app.luxion.shogunai.infrastructure", "val a = app.luxion.shogunai.domain.usecase.parse()")
        val useCase = core("domain/usecase/U.kt", "app.luxion.shogunai.domain.usecase", "import app.luxion.shogunai.infrastructure.NioFileManager", "import app.luxion.shogunai.BuildInfo")
        val stray = core("other/O.kt", "app.luxion.shogunai.other")
        assertEquals(listOf(3, 3, 3, 1), CoreLayers.violations(tree(port, adapter, useCase, stray)).map { it.substringAfter(".kt:").substringBefore(':').toInt() })
    }

    @Test
    fun `the engine wires use cases, and nothing in core imports it`() {
        val engine = core("engine/E.kt", "app.luxion.shogunai.engine", "import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase", "import app.luxion.shogunai.infrastructure.NioFileManager")
        val adapter = core("infrastructure/I.kt", "app.luxion.shogunai.infrastructure", "import app.luxion.shogunai.engine.ShogunEngine")
        val useCase = core("domain/usecase/U.kt", "app.luxion.shogunai.domain.usecase", "import app.luxion.shogunai.engine.ShogunEngine")
        assertEquals(
            listOf(
                "core/src/main/kotlin/app/luxion/shogunai/engine/E.kt:4: app.luxion.shogunai.engine must not depend on app.luxion.shogunai.infrastructure (app.luxion.shogunai.infrastructure.NioFileManager)",
                "core/src/main/kotlin/app/luxion/shogunai/infrastructure/I.kt:3: app.luxion.shogunai.infrastructure must not depend on app.luxion.shogunai.engine (app.luxion.shogunai.engine.ShogunEngine)",
                "$domain/usecase/U.kt:3: app.luxion.shogunai.domain.usecase must not depend on app.luxion.shogunai.engine (app.luxion.shogunai.engine.ShogunEngine)",
            ),
            CoreLayers.violations(tree(engine, adapter, useCase)),
        )
    }

    @Test
    fun `system access outside the list fails, and a stale entry fails too`() {
        val useCase = core("domain/usecase/U.kt", "app.luxion.shogunai.domain.usecase", "import java.io.File", "val p = System.getProperty(\"os.name\")")
        val listed = core("domain/model/M.kt", "app.luxion.shogunai.domain.model", "import java.nio.file.Path")
        val adapter = core("infrastructure/I.kt", "app.luxion.shogunai.infrastructure", "import java.io.File")
        val exceptions = mapOf(
            "$domain/model/M.kt" to setOf("java.nio.file", "kotlin.io.path"),
            "$domain/model/Gone.kt" to setOf("ProcessBuilder"),
        )
        assertEquals(
            listOf(
                "$domain/usecase/U.kt:3: the domain must not use java.io.File",
                "$domain/usecase/U.kt:4: the domain must not use System.getProperty",
                "$domain/model/M.kt: no longer uses kotlin.io.path, remove it from the system access exceptions",
                "$domain/model/Gone.kt: no longer uses ProcessBuilder, remove it from the system access exceptions",
            ),
            DomainSystemAccess.violations(tree(useCase, listed, adapter), exceptions),
        )
    }

    @Test
    fun `every system api is recognised`() {
        val text = listOf("import java.io.*", "import java.nio.file.Files", "import kotlin.io.path.readText", "val a = System::getProperty", "val b = ProcessBuilder()", "val c = Runtime.getRuntime()", "val d = \"java.io.File\"")
        val found = DomainSystemAccess.found(tree(core("domain/usecase/U.kt", "app.luxion.shogunai.domain.usecase", *text.toTypedArray())))
        assertEquals(DomainSystemAccess.apis.keys.toList(), found.map { it.api })
    }

    @Test
    fun `infrastructure outside the composition roots and CLI internals in the desktop app fail`() {
        val screen = main("desktopApp/src/main/kotlin/app/luxion/shogunai/ui/Screen.kt", "package app.luxion.shogunai.ui\nimport app.luxion.shogunai.infrastructure.NioFileManager\nimport app.luxion.shogunai.cli.CliCommand\n")
        val root = main("desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt", "package app.luxion.shogunai\nimport app.luxion.shogunai.infrastructure.NioFileManager\nimport app.luxion.shogunai.cli.runShogun\n")
        val desktopAdapter = main("desktopApp/src/main/kotlin/app/luxion/shogunai/infrastructure/Clipboard.kt", "package app.luxion.shogunai.infrastructure\nval a = app.luxion.shogunai.infrastructure.Other()\n")
        val cliText = main("cli/src/main/kotlin/app/luxion/shogunai/cli/HelpText.kt", "package app.luxion.shogunai.cli\nimport app.luxion.shogunai.infrastructure.NioFileManager\n")
        val cliRoot = main("cli/src/main/kotlin/app/luxion/shogunai/cli/Main.kt", "package app.luxion.shogunai.cli\nimport app.luxion.shogunai.infrastructure.NioFileManager\n")
        assertEquals(
            listOf(
                "desktopApp/src/main/kotlin/app/luxion/shogunai/ui/Screen.kt:2: only main.kt, AppContainer.kt may use infrastructure (app.luxion.shogunai.infrastructure.NioFileManager)",
                "cli/src/main/kotlin/app/luxion/shogunai/cli/HelpText.kt:2: only Main.kt, ShogunCli.kt may use infrastructure (app.luxion.shogunai.infrastructure.NioFileManager)",
                "desktopApp/src/main/kotlin/app/luxion/shogunai/ui/Screen.kt:3: the desktop app may only use app.luxion.shogunai.cli.runShogun from the CLI (app.luxion.shogunai.cli.CliCommand)",
            ),
            CompositionRoots.violations(tree(screen, root, desktopAdapter, cliText, cliRoot)),
        )
    }

    @Test
    fun `comments fail in any source set and in gradle scripts`() {
        val test = KotlinSource("cli/src/test/kotlin/ATest.kt", "cli", "test", "val retries = 3 // enough for CI\n")
        val script = KotlinSource("build.gradle.kts", "root", "gradle", "/**\n * Docs\n */\nplugins {}\n")
        val clean = main("core/src/main/kotlin/A.kt", "val url = \"https://herdr.dev\"\n")
        assertEquals(
            listOf("cli/src/test/kotlin/ATest.kt:1: comment: // enough for CI", "build.gradle.kts:1: comment: /**"),
            NoComments.violations(SourceTree(listOf(test, clean), listOf(script))),
        )
    }

    @Test
    fun `shells outside the list and Runtime exec fail, and a stale entry fails too`() {
        val useCase = core("domain/usecase/U.kt", "app.luxion.shogunai.domain.usecase", "val c = listOf(\"bash\", \"-c\", script)", "val p = Runtime.getRuntime().exec(line)")
        val listed = core("infrastructure/Editor.kt", "app.luxion.shogunai.infrastructure", "val c = listOf(\"sh\", \"-c\", detached)")
        val hooks = core("domain/usecase/Hooks.kt", "app.luxion.shogunai.domain.usecase", "val matcher = \"Bash\"")
        val test = KotlinSource("core/src/test/kotlin/ATest.kt", "core", "test", "val c = listOf(\"zsh\")\n")
        val gone = "core/src/main/kotlin/app/luxion/shogunai/infrastructure/Gone.kt"
        assertEquals(
            listOf(
                "$domain/usecase/U.kt:4: commands run as argv through the executor, never Runtime.exec",
                "$domain/usecase/U.kt:3: runs the shell \"bash\"; pass commands as argv",
                "$gone: no longer names a shell, remove it from the argv exceptions",
            ),
            ArgvCommands.violations(tree(useCase, listed, hooks, test), setOf(listed.path, gone)),
        )
    }

    @Test
    fun `long files fail unless capped, and caps fail once stale`() {
        fun lines(count: Int) = "val a = 1\n".repeat(count)
        val large = main("core/src/main/kotlin/Large.kt", lines(450))
        val grown = main("core/src/main/kotlin/Grown.kt", lines(510))
        val capped = main("core/src/main/kotlin/Capped.kt", lines(600))
        val shrunk = main("core/src/main/kotlin/Shrunk.kt", lines(400))
        val test = KotlinSource("core/src/test/kotlin/LongTest.kt", "core", "test", lines(900))
        val caps = mapOf(grown.path to 500, capped.path to 600, shrunk.path to 420, "core/src/main/kotlin/Gone.kt" to 800)
        assertEquals(
            listOf(
                "core/src/main/kotlin/Large.kt: has 450 lines, over its cap of 400",
                "core/src/main/kotlin/Grown.kt: has 510 lines, over its cap of 500",
                "core/src/main/kotlin/Shrunk.kt: has 400 lines, remove it from the file length caps",
                "core/src/main/kotlin/Gone.kt: no longer exists, remove it from the file length caps",
            ),
            FileLength.violations(tree(large, grown, capped, shrunk, test), caps),
        )
    }
}
