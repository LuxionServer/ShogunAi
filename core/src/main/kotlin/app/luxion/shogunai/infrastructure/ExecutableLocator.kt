package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.HostOs
import java.io.File

class ExecutableLocator(
    private val os: HostOs = HostOs.current(),
    private val home: String = System.getProperty("user.home"),
    private val pathFolders: List<String> = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() },
    private val localAppData: String? = System.getenv("LOCALAPPDATA"),
    private val pathExtensions: List<String> = executableExtensions(System.getenv("PATHEXT")),
    private val isFile: (String) -> Boolean = { File(it).isFile },
) {
    fun find(executable: String, includeToolbox: Boolean = false): String? {
        val separator = if (os == HostOs.WINDOWS) "\\" else "/"
        val toolbox = if (includeToolbox) ToolCatalog.toolboxScriptFolders(os, home, localAppData) else emptyList()
        val folders = pathFolders + ToolCatalog.userBinFolders(os, home) + toolbox
        val names = if (os == HostOs.WINDOWS && '.' !in executable) listOf(executable) + pathExtensions.map { executable + it } else listOf(executable)
        return folders.flatMap { folder -> names.map { folder.trimEnd('/', '\\') + separator + it } }.firstOrNull(isFile)
    }

    fun userBinFolders(): List<String> = ToolCatalog.userBinFolders(os, home)

    companion object {
        private val DefaultExtensions = listOf(".com", ".exe", ".bat", ".cmd")

        fun executableExtensions(pathExt: String?): List<String> =
            pathExt.orEmpty().split(';').map { it.trim().lowercase() }.filter { it.startsWith(".") }.ifEmpty { DefaultExtensions }
    }
}
