package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.KmpTestWiring
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_UDID
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

class FileKmpTestWiring(
    private val gradleUserHome: String = defaultGradleUserHome(),
) : KmpTestWiring {

    override fun isWiredInRepository(repositoryPath: String): Boolean =
        anyBuildScript(repositoryPath) { it.contains(IOS_SIMULATOR_UDID) }

    override fun usesKotlinMultiplatform(repositoryPath: String): Boolean =
        anyBuildScript(repositoryPath) { it.contains(MULTIPLATFORM, ignoreCase = true) }

    private fun anyBuildScript(repositoryPath: String, matches: (String) -> Boolean): Boolean {
        val root = Path.of(repositoryPath)
        if (!Files.isDirectory(root)) return false
        var found = false
        Files.walkFileTree(
            root,
            emptySet(),
            MAX_DEPTH,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir != root && dir.name in SKIPPED_DIRECTORIES) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile && (file.name.endsWith(".gradle.kts") || file.name.endsWith(".gradle")) &&
                        matches(runCatching { file.readText() }.getOrDefault(""))
                    ) {
                        found = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return found
    }

    override fun isInitScriptInstalled(): Boolean = initScript().exists()

    override fun initScriptPath(): String = initScript().toString()

    override fun installInitScript() {
        val script = initScript()
        Files.createDirectories(script.parent)
        Files.writeString(script, INIT_SCRIPT)
    }

    override fun uninstallInitScript() {
        val script = initScript()
        Files.deleteIfExists(script)
        val directory = script.parent
        if (Files.isDirectory(directory) && Files.list(directory).use { it.findAny().isEmpty }) Files.delete(directory)
    }

    private fun initScript(): Path = Path.of(gradleUserHome, "init.d", INIT_SCRIPT_NAME)

    companion object {
        const val INIT_SCRIPT_NAME = "shogunai-ios-simulator.init.gradle.kts"
        private const val MAX_DEPTH = 5
        private const val MULTIPLATFORM = "multiplatform"
        private val SKIPPED_DIRECTORIES = setOf("build", ".gradle", ".git", "node_modules", ".kotlin", ".idea")

        val INIT_SCRIPT = """
            val udid: String? = System.getenv("$IOS_SIMULATOR_UDID")
            if (udid != null) {
                allprojects {
                    tasks.configureEach {
                        if (javaClass.name.startsWith("org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest")) {
                            @Suppress("UNCHECKED_CAST")
                            (javaClass.getMethod("getDevice").invoke(this) as org.gradle.api.provider.Property<String>).set(udid)
                        }
                    }
                }
            }

        """.trimIndent()

        val REPOSITORY_SNIPPET = """
            tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
                providers.environmentVariable("$IOS_SIMULATOR_UDID").orNull?.let { device.set(it) }
            }
        """.trimIndent()
    }
}
