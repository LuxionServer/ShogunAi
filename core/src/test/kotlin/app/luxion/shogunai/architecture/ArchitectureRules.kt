package app.luxion.shogunai.architecture

private const val DOMAIN = "$PROJECT_PACKAGE.domain"
private const val MODEL = "$DOMAIN.model"
private const val EXECUTOR = "$DOMAIN.executor"
private const val IO = "$DOMAIN.io"
private const val USECASE = "$DOMAIN.usecase"
private const val ENGINE = "$PROJECT_PACKAGE.engine"
const val INFRASTRUCTURE = "$PROJECT_PACKAGE.infrastructure"
const val DOMAIN_SOURCES = "core/src/main/kotlin/app/luxion/shogunai/domain"

private fun String.isIn(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

private fun KotlinSource.violation(line: Int, message: String): String = "${location(line)}: $message"

private fun staleEntry(path: String, message: String): String = "$path: $message"

object CoreLayers {

    val allowed: Map<String, Set<String>> = mapOf(
        MODEL to emptySet(),
        EXECUTOR to emptySet(),
        IO to setOf(MODEL, EXECUTOR),
        USECASE to setOf(MODEL, EXECUTOR, IO),
        INFRASTRUCTURE to setOf(MODEL, EXECUTOR, IO),
        ENGINE to setOf(MODEL, EXECUTOR, IO, USECASE),
    )

    private fun layerOf(name: String): String? = allowed.keys.firstOrNull { name.isIn(it) }

    fun violations(tree: SourceTree): List<String> = tree.production("core").flatMap { source ->
        val layer = layerOf(source.packageName)
            ?: return@flatMap listOf(source.violation(1, "package ${source.packageName} is outside the core layers"))
        source.projectReferences.mapNotNull { reference ->
            val target = layerOf(reference.target)
            if (target == null || target == layer || target in allowed.getValue(layer)) {
                null
            } else {
                source.violation(reference.line, "$layer must not depend on $target (${reference.target})")
            }
        }
    }
}

data class SystemAccess(val path: String, val line: Int, val api: String)

object DomainSystemAccess {

    val apis: Map<String, Regex> = mapOf(
        "java.io.File" to Regex("""\bjava\.io\.(File\b|\*)"""),
        "java.nio.file" to Regex("""\bjava\.nio\.file\b"""),
        "kotlin.io.path" to Regex("""\bkotlin\.io\.path\b"""),
        "System.getProperty" to Regex("""\bSystem\s*(\.|::)\s*getPropert"""),
        "ProcessBuilder" to Regex("""\bProcessBuilder\b"""),
        "Runtime" to Regex("""\bRuntime\s*\.\s*getRuntime\b"""),
    )

    fun found(tree: SourceTree): List<SystemAccess> =
        tree.production("core").filter { it.path.startsWith("$DOMAIN_SOURCES/") }.flatMap { source ->
            apis.mapNotNull { (api, pattern) ->
                source.scanned.code.indexOfFirst { pattern.containsMatchIn(it) }
                    .takeIf { it >= 0 }
                    ?.let { SystemAccess(source.path, it + 1, api) }
            }
        }

    fun violations(tree: SourceTree, exceptions: Map<String, Set<String>>): List<String> {
        val found = found(tree)
        val unlisted = found.filterNot { it.api in exceptions[it.path].orEmpty() }
            .map { "${it.path}:${it.line}: the domain must not use ${it.api}" }
        val foundPairs = found.map { it.path to it.api }.toSet()
        val stale = exceptions.flatMap { (path, listed) -> listed.map { path to it } }
            .filterNot { it in foundPairs }
            .map { (path, api) -> staleEntry(path, "no longer uses $api, remove it from the system access exceptions") }
        return unlisted + stale
    }
}

object CompositionRoots {

    const val CLI_PACKAGE = "$PROJECT_PACKAGE.cli"
    const val CLI_ENTRY_POINT = "$CLI_PACKAGE.runShogun"

    val desktopRoots = setOf(
        "desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt",
        "desktopApp/src/main/kotlin/app/luxion/shogunai/AppContainer.kt",
    )

    val cliRoots = setOf(
        "cli/src/main/kotlin/app/luxion/shogunai/cli/Main.kt",
        "cli/src/main/kotlin/app/luxion/shogunai/cli/ShogunCli.kt",
    )

    fun violations(tree: SourceTree): List<String> =
        infrastructureOutsideRoots(tree, "desktopApp", desktopRoots) +
            infrastructureOutsideRoots(tree, "cli", cliRoots) +
            cliInternalsInDesktop(tree)

    private fun infrastructureOutsideRoots(tree: SourceTree, module: String, roots: Set<String>): List<String> =
        tree.production(module)
            .filterNot { it.path in roots || it.packageName.isIn(INFRASTRUCTURE) }
            .flatMap { source ->
                source.projectReferences.filter { it.target.isIn(INFRASTRUCTURE) }
                    .map { source.violation(it.line, "only ${roots.joinToString { root -> root.substringAfterLast('/') }} may use infrastructure (${it.target})") }
            }

    private fun cliInternalsInDesktop(tree: SourceTree): List<String> =
        tree.production("desktopApp").flatMap { source ->
            source.projectReferences.filter { it.target.isIn(CLI_PACKAGE) && it.target != CLI_ENTRY_POINT }
                .map { source.violation(it.line, "the desktop app may only use $CLI_ENTRY_POINT from the CLI (${it.target})") }
        }
}

object NoComments {

    fun violations(tree: SourceTree): List<String> = tree.allSources.flatMap { source ->
        source.scanned.comments.map { source.violation(it.line, "comment: ${it.text.lineSequence().first().trim()}") }
    }
}

object ArgvCommands {

    private val shell = Regex("""(sh|bash|zsh|fish|cmd|powershell|pwsh)(\.exe)?""")
    private val runtimeExec = Regex("""\bRuntime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\b""")

    fun violations(tree: SourceTree, exceptions: Set<String>): List<String> {
        val production = tree.kotlinSources.filter { it.isProduction }
        val exec = production.flatMap { source ->
            source.scanned.code.withIndex().filter { runtimeExec.containsMatchIn(it.value) }
                .map { source.violation(it.index + 1, "commands run as argv through the executor, never Runtime.exec") }
        }
        val shells = production.mapNotNull { source ->
            source.scanned.literals.firstOrNull { shell.matches(it.text) }?.let { source to it }
        }
        val unlisted = shells.filterNot { (source, _) -> source.path in exceptions }
            .map { (source, literal) -> source.violation(literal.line, "runs the shell \"${literal.text}\"; pass commands as argv") }
        val shellPaths = shells.map { it.first.path }.toSet()
        val stale = exceptions.filterNot { it in shellPaths }
            .map { staleEntry(it, "no longer names a shell, remove it from the argv exceptions") }
        return exec + unlisted + stale
    }
}

object FileLength {

    const val MAX_LINES = 400

    fun violations(tree: SourceTree, caps: Map<String, Int>): List<String> {
        val production = tree.kotlinSources.filter { it.isProduction }
        val tooLong = production.mapNotNull { source ->
            val cap = caps[source.path] ?: MAX_LINES
            if (source.lineCount <= cap) {
                null
            } else {
                staleEntry(source.path, "has ${source.lineCount} lines, over its cap of $cap")
            }
        }
        val lengths = production.associate { it.path to it.lineCount }
        val stale = caps.keys.mapNotNull { path ->
            when (val length = lengths[path]) {
                null -> staleEntry(path, "no longer exists, remove it from the file length caps")
                in 0..MAX_LINES -> staleEntry(path, "has $length lines, remove it from the file length caps")
                else -> null
            }
        }
        return tooLong + stale
    }
}
