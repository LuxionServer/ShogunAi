package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assume.assumeTrue
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileKmpTestWiringTest {

    private val root: Path = Files.createTempDirectory("kmp-wiring")
    private val repository: Path = root.resolve("main-repo").createDirectories()
    private val gradleHome: Path = root.resolve("gradle-home")
    private val wiring = FileKmpTestWiring(gradleHome.toString())

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `finds the variable in a module build script`() {
        repository.resolve("shared").createDirectories().resolve("build.gradle.kts")
            .writeText(FileKmpTestWiring.REPOSITORY_SNIPPET)

        assertTrue(wiring.isWiredInRepository(repository.toString()))
    }

    @Test
    fun `ignores build outputs and repositories without the variable`() {
        repository.resolve("build.gradle.kts").writeText("""plugins { kotlin("multiplatform") }""")
        repository.resolve("shared/build/generated").createDirectories().resolve("copy.gradle.kts").writeText("IOS_SIMULATOR_UDID")

        assertFalse(wiring.isWiredInRepository(repository.toString()))
    }

    @Test
    fun `detects the Kotlin Multiplatform plugin by id or version catalog alias`() {
        val byId = root.resolve("by-id").createDirectories()
        byId.resolve("shared").createDirectories().resolve("build.gradle.kts").writeText("""plugins { kotlin("multiplatform") }""")
        val byAlias = root.resolve("by-alias").createDirectories()
        byAlias.resolve("composeApp").createDirectories().resolve("build.gradle.kts")
            .writeText("plugins { alias(libs.plugins.kotlinMultiplatform) }")

        assertTrue(wiring.usesKotlinMultiplatform(byId.toString()))
        assertTrue(wiring.usesKotlinMultiplatform(byAlias.toString()))
    }

    @Test
    fun `a JVM-only build or a repository without Gradle isn't Kotlin Multiplatform`() {
        repository.resolve("build.gradle.kts").writeText("""plugins { kotlin("jvm") }""")
        val xcodeOnly = root.resolve("xcode-only").createDirectories()
        xcodeOnly.resolve("App.xcodeproj").createDirectories().resolve("project.pbxproj").writeText("// multiplatform")

        assertFalse(wiring.usesKotlinMultiplatform(repository.toString()))
        assertFalse(wiring.usesKotlinMultiplatform(xcodeOnly.toString()))
    }

    @Test
    fun `installs and uninstalls the init script in the Gradle user home`() {
        assertFalse(wiring.isInitScriptInstalled())

        wiring.installInitScript()

        val script = gradleHome.resolve("init.d/shogunai-ios-simulator.init.gradle.kts")
        assertEquals(script.toString(), wiring.initScriptPath())
        assertTrue(wiring.isInitScriptInstalled())
        assertTrue(script.readText().contains("System.getenv(\"IOS_SIMULATOR_UDID\")"))
        assertTrue(script.readText().contains("getMethod(\"getDevice\")"))
        assertEquals(FileKmpTestWiring.INIT_SCRIPT, script.readText())

        wiring.uninstallInitScript()
        assertFalse(wiring.isInitScriptInstalled())
        assertFalse(gradleHome.resolve("init.d").exists())
    }

    @Test
    fun `uninstalling keeps the user's other init scripts`() {
        wiring.installInitScript()
        val other = gradleHome.resolve("init.d/mirror.init.gradle.kts").apply { writeText("") }

        wiring.uninstallInitScript()

        assertTrue(other.exists())
    }

    @Test
    fun `an unreadable folder is skipped while looking for build scripts`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val locked = repository.resolve("locked").createDirectories()
        locked.resolve("build.gradle.kts").writeText("plugins { kotlin(\"multiplatform\") }")
        Files.setPosixFilePermissions(locked, emptySet())
        try {
            assertFalse(wiring.usesKotlinMultiplatform(repository.toString()))
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"))
        }
    }

    @Test
    fun `a missing repository, unreadable scripts, links and folders named like scripts are ignored`() {
        assertFalse(FileKmpTestWiring(gradleHome.toString()).usesKotlinMultiplatform(root.resolve("missing").toString()))
        val plugin = "plugins { kotlin(\"multiplatform\") }"
        repository.resolve("folder.gradle.kts").createDirectories()
        val outside = root.resolve("outside.gradle.kts").apply { writeText(plugin) }
        Files.createSymbolicLink(repository.resolve("linked.gradle.kts"), outside)
        repository.resolve("notes.txt").writeText(plugin)
        val unreadable = repository.resolve("build.gradle").apply { writeText(plugin) }
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"))
        try {
            assertFalse(wiring.usesKotlinMultiplatform(repository.toString()))
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-------"))
        }
        assertTrue(wiring.usesKotlinMultiplatform(repository.toString()))
    }

    @Test
    fun `uninstalling a script that was never installed does nothing`() {
        wiring.uninstallInitScript()

        assertFalse(gradleHome.exists())
    }
}
