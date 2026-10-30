package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.model.HostOs
import java.io.File

class ExecutableLocator(
    private val os: HostOs,
    private val home: String,
    private val pathFolders: () -> List<String>,
    val localAppData: String? = null,
    val programFolders: List<String> = emptyList(),
    val pathExtensions: List<String> = DEFAULT_PATH_EXTENSIONS,
    private val userBinFolders: List<String> = ToolCatalog.userBinFolders(os, home),
    val subfolders: (String) -> List<String> = { folder -> File(folder).listFiles { entry -> entry.isDirectory }.orEmpty().map { it.name } },
    val isFile: (String) -> Boolean = { File(it).isFile },
) {
    constructor(environment: HostEnvironment) : this(
        os = environment.os,
        home = environment.home,
        pathFolders = { environment.path.folders },
        localAppData = environment.variable("LOCALAPPDATA"),
        programFolders = listOfNotNull(environment.variable("ProgramFiles"), environment.variable("ProgramFiles(x86)")),
        pathExtensions = environment.variable("PATHEXT").orEmpty().split(';').filter { it.isNotBlank() }.ifEmpty { DEFAULT_PATH_EXTENSIONS },
        userBinFolders = environment.userBinFolders,
    )

    fun find(executable: String, includeToolbox: Boolean = false): String? {
        val separator = if (os == HostOs.WINDOWS) "\\" else "/"
        val toolbox = if (includeToolbox) ToolCatalog.toolboxScriptFolders(os, home, localAppData) else emptyList()
        val folders = pathFolders() + userBinFolders + toolbox
        val names = if (os == HostOs.WINDOWS && '.' !in executable) listOf(executable) + pathExtensions.map { executable + it } else listOf(executable)
        return folders.flatMap { folder -> names.map { folder.trimEnd('/', '\\') + separator + it } }.firstOrNull(isFile)
    }

    fun userBinFolders(): List<String> = userBinFolders

    private companion object {
        private val DEFAULT_PATH_EXTENSIONS = listOf(".COM", ".EXE", ".BAT", ".CMD")
    }
}
