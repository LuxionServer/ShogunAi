package app.luxion.shogunai.architecture

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

class SourceTree(val kotlinSources: List<KotlinSource>, val gradleScripts: List<KotlinSource>) {

    val allSources: List<KotlinSource> get() = kotlinSources + gradleScripts

    fun production(module: String): List<KotlinSource> = kotlinSources.filter { it.module == module && it.isProduction }

    companion object {
        val MODULES = listOf("core", "cli", "desktopApp")

        val repository: SourceTree by lazy { load(Path.of("").toAbsolutePath().parent) }

        fun load(root: Path, modules: List<String> = MODULES): SourceTree {
            val missing = modules.filterNot { root.resolve("$it/src").isDirectory() }
            require(missing.isEmpty()) { "Missing sources of $missing under $root" }
            val kotlin = modules.flatMap { module ->
                root.resolve("$module/src").walk()
                    .filter { it.isRegularFile() && it.extension == "kt" }
                    .map { source(root, it, module, sourceSetOf(root.resolve("$module/src"), it)) }
                    .toList()
            }
            val scripts = (listOf(root) + modules.map { root.resolve(it) })
                .flatMap { folder -> folder.toFile().listFiles().orEmpty().map { it.toPath() } }
                .filter { it.isRegularFile() && it.name.endsWith(".gradle.kts") }
                .map { source(root, it, root.relativize(it).parent?.toString() ?: "root", "gradle") }
            return SourceTree(kotlin.sortedBy { it.path }, scripts.sortedBy { it.path })
        }

        private fun sourceSetOf(sources: Path, file: Path): String = sources.relativize(file).first().toString()

        private fun source(root: Path, file: Path, module: String, sourceSet: String) =
            KotlinSource(root.relativize(file).invariantSeparatorsPathString, module, sourceSet, file.readText())
    }
}
