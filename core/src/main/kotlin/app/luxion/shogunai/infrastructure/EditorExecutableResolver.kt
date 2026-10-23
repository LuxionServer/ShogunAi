package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs

fun interface UninstallLocations {
    suspend fun find(): List<String>
}

class RegistryUninstallLocations(private val executor: ShellCommandExecutor) : UninstallLocations {

    override suspend fun find(): List<String> = KEYS.flatMap { key -> query(key) }.distinct()

    private suspend fun query(key: String): List<String> = try {
        val result = executor.executeWithin(listOf("reg", "query", key, "/s", "/v", "InstallLocation"), CommandTimeouts.LISTING)
        if (result.isSuccess) parse(result.stdout) else emptyList()
    } catch (_: CommandExecutionException) {
        emptyList()
    }

    companion object {
        private const val UNINSTALL = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
        private val KEYS = listOf("HKLM\\$UNINSTALL", "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall", "HKCU\\$UNINSTALL")
        private val VALUE = Regex("""^\s+InstallLocation\s+REG_SZ\s+(\S.*?)\s*$""")

        fun parse(output: String): List<String> =
            output.lineSequence().mapNotNull { VALUE.find(it)?.groupValues?.get(1) }.distinct().toList()
    }
}

class UninstallSnapshot(private val source: UninstallLocations) {
    private var locations: List<String>? = null

    suspend fun locations(): List<String> = locations ?: source.find().also { locations = it }
}

class EditorExecutableResolver(
    private val os: HostOs,
    private val locator: ExecutableLocator,
    private val uninstall: UninstallLocations = UninstallLocations { emptyList() },
) {

    fun snapshot(): UninstallSnapshot = UninstallSnapshot(uninstall)

    suspend fun find(editor: EditorId, snapshot: UninstallSnapshot = snapshot()): String? {
        if (os != HostOs.WINDOWS) {
            val name = ToolCatalog.editorExecutable(editor, os) ?: return null
            return locator.find(name, includeToolbox = true)
        }
        return onPath(editor)
            ?: inInstallFolders(editor)
            ?: inToolbox(editor)
            ?: inUninstallLocations(editor, snapshot)
    }

    private fun candidateFiles(editor: EditorId): List<String> =
        ToolCatalog.windowsCommandNames(editor).flatMap { name -> locator.pathExtensions.map { name + it.lowercase() } }

    private fun onPath(editor: EditorId): String? =
        candidateFiles(editor).firstNotNullOfOrNull { locator.find(it) }

    private fun inInstallFolders(editor: EditorId): String? {
        val roots = locator.programFolders + listOfNotNull(locator.localAppData?.let { "$it\\Programs" })
        for (install in ToolCatalog.windowsInstalls(editor)) {
            for (root in roots) {
                val parent = install.parent?.let { join(root, it) } ?: root
                for (folder in installFolders(parent, install)) {
                    install.executables.map { join(folder, it) }.firstOrNull(locator.isFile)?.let { return it }
                }
            }
        }
        return null
    }

    private fun installFolders(parent: String, install: WindowsInstall): List<String> {
        val exact = join(parent, install.folder)
        if (!install.versioned) return listOf(exact)
        val matching = locator.subfolders(parent).filter { it.startsWith(install.folder, ignoreCase = true) }
        return (listOf(exact) + matching.sortedWith(NEWEST_FIRST).map { join(parent, it) }).distinct()
    }

    private fun inToolbox(editor: EditorId): String? {
        val scripts = candidateFiles(editor).firstNotNullOfOrNull { locator.find(it, includeToolbox = true) }
        if (scripts != null) return scripts
        val localAppData = locator.localAppData ?: return null
        val apps = ToolCatalog.windowsToolboxAppsFolder(localAppData)
        val products = ToolCatalog.windowsToolboxProducts(editor)
        val executables = ToolCatalog.windowsInstalls(editor).flatMap { it.executables }
        for (product in locator.subfolders(apps).filter { name -> products.any { name.startsWith(it, ignoreCase = true) } }) {
            for (channel in locator.subfolders(join(apps, product))) {
                val channelFolder = join(join(apps, product), channel)
                for (build in locator.subfolders(channelFolder).sortedWith(NEWEST_FIRST)) {
                    executables.map { join(join(channelFolder, build), it) }.firstOrNull(locator.isFile)?.let { return it }
                }
            }
        }
        return null
    }

    private suspend fun inUninstallLocations(editor: EditorId, snapshot: UninstallSnapshot): String? {
        val marker = ToolCatalog.windowsRegistryMarker(editor) ?: return null
        val executables = ToolCatalog.windowsInstalls(editor).flatMap { it.executables }
        return snapshot.locations()
            .filter { it.lowercase().contains(marker) }
            .firstNotNullOfOrNull { location -> executables.map { join(location, it) }.firstOrNull(locator.isFile) }
    }

    private fun join(folder: String, name: String): String = folder.trimEnd('/', '\\') + "\\" + name

    private companion object {
        val NUMBERS = Regex("""\d+""")

        val NEWEST_FIRST = Comparator<String> { first, second ->
            val a = NUMBERS.findAll(first).map { it.value.toBigInteger() }.toList()
            val b = NUMBERS.findAll(second).map { it.value.toBigInteger() }.toList()
            var order = 0
            for (index in 0 until maxOf(a.size, b.size)) {
                val left = a.getOrNull(index) ?: java.math.BigInteger.valueOf(-1)
                val right = b.getOrNull(index) ?: java.math.BigInteger.valueOf(-1)
                order = right.compareTo(left)
                if (order != 0) break
            }
            order
        }
    }
}
