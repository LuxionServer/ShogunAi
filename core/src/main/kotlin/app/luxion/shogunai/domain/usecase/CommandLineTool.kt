package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.LauncherFiles
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HostOs

enum class CommandLineToolState { AVAILABLE, INSTALLED_NOT_ON_PATH, BROKEN, MISSING }

data class CommandLineToolStatus(
    val state: CommandLineToolState,
    val launcher: String? = null,
    val folder: String? = null,
    val restartNeeded: Boolean = false,
    val canInstall: Boolean = false,
    val installedByApp: Boolean = false,
) {
    val usableByHooks: Boolean
        get() = state == CommandLineToolState.AVAILABLE || (state == CommandLineToolState.INSTALLED_NOT_ON_PATH && restartNeeded)
}

data class CommandLineToolInstallation(val launcher: String, val folder: String, val restartNeeded: Boolean, val folderOnPath: Boolean)

sealed class CommandLineToolError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotPackaged : CommandLineToolError("This copy of ShogunAi isn't an installed app, so it has no launcher to expose")
    class ForeignFile(val path: String) : CommandLineToolError("$path already exists and wasn't created by ShogunAi")
    class WriteFailed(val path: String, cause: Throwable) : CommandLineToolError("Could not write $path: ${cause.message}", cause)
}

class CommandLineTool(
    private val os: HostOs,
    private val appLauncher: String?,
    private val home: String,
    private val pathFolders: () -> List<String>,
    private val userBinFolders: List<String>,
    private val findShogun: () -> String?,
    private val files: LauncherFiles,
    private val userPath: UserPathEntries,
) {
    private val separator = if (os == HostOs.WINDOWS) "\\" else "/"
    private val windowsFolder = "$home\\.shogunai\\bin"

    suspend fun status(): CommandLineToolStatus {
        val canInstall = appLauncher != null
        val owned = ownedLauncher()
        if (owned != null && !targetExists(owned)) {
            return CommandLineToolStatus(CommandLineToolState.BROKEN, owned, folderOf(owned), canInstall = canInstall, installedByApp = true)
        }
        val found = owned ?: findShogun()
        if (found == null) return CommandLineToolStatus(CommandLineToolState.MISSING, canInstall = canInstall)
        val folder = folderOf(found)
        if (isOnPath(folder)) return CommandLineToolStatus(CommandLineToolState.AVAILABLE, found, folder, canInstall = canInstall, installedByApp = owned != null)
        val restartNeeded = os == HostOs.WINDOWS && userPath.contains(folder)
        return CommandLineToolStatus(CommandLineToolState.INSTALLED_NOT_ON_PATH, found, folder, restartNeeded, canInstall, installedByApp = owned != null)
    }

    suspend fun install(): Result<CommandLineToolInstallation> = runCatching {
        val launcher = appLauncher ?: throw CommandLineToolError.NotPackaged()
        if (os == HostOs.WINDOWS) installShim(launcher) else installLink(launcher)
    }

    suspend fun remove(): Result<Unit> = runCatching {
        val owned = ownedLauncher() ?: return@runCatching
        try {
            files.delete(owned)
        } catch (error: Exception) {
            throw CommandLineToolError.WriteFailed(owned, error)
        }
        if (os == HostOs.WINDOWS) userPath.remove(folderOf(owned))
    }

    private fun installLink(launcher: String): CommandLineToolInstallation {
        val folder = linkFolders().firstOrNull { files.isWritableFolder(it) && isOnPath(it) } ?: "$home/.local/bin"
        val link = "$folder/shogun"
        if (files.exists(link) || files.linkTarget(link) != null) {
            if (!isOwnLink(link)) throw CommandLineToolError.ForeignFile(link)
        }
        try {
            files.createLink(launcher, link)
        } catch (error: Exception) {
            throw CommandLineToolError.WriteFailed(link, error)
        }
        return CommandLineToolInstallation(link, folder, restartNeeded = false, folderOnPath = isOnPath(folder))
    }

    private suspend fun installShim(launcher: String): CommandLineToolInstallation {
        val shim = "$windowsFolder\\shogun.cmd"
        val installFolder = launcher.substringBeforeLast('\\')
        if (files.exists(shim) && files.readText(shim)?.contains(ShimMarker) != true) throw CommandLineToolError.ForeignFile(shim)
        val body = "@\"$installFolder\\runtime\\bin\\java.exe\" -cp \"$installFolder\\app\\*\" $ShimMarker %*\r\n"
        try {
            files.writeText(shim, body)
        } catch (error: Exception) {
            throw CommandLineToolError.WriteFailed(shim, error)
        }
        if (!userPath.contains(windowsFolder)) userPath.add(windowsFolder)
        return CommandLineToolInstallation(shim, windowsFolder, restartNeeded = true, folderOnPath = isOnPath(windowsFolder))
    }

    private fun linkFolders(): List<String> = userBinFolders

    private fun ownedLauncher(): String? =
        if (os == HostOs.WINDOWS) "$windowsFolder\\shogun.cmd".takeIf { files.exists(it) && files.readText(it)?.contains(ShimMarker) == true }
        else (linkFolders() + "$home/.local/bin").distinct().map { "$it/shogun" }.firstOrNull { isOwnLink(it) }

    private fun isOwnLink(link: String): Boolean =
        files.linkTarget(link)?.substringAfterLast('/')?.startsWith(AppLauncherName, ignoreCase = true) == true

    private fun targetExists(owned: String): Boolean =
        if (os == HostOs.WINDOWS) files.readText(owned)?.let { body -> windowsJava(body)?.let(files::exists) } ?: false
        else files.linkTarget(owned)?.let(files::exists) ?: false

    private fun windowsJava(body: String): String? = Regex("@\"([^\"]+java\\.exe)\"").find(body)?.groupValues?.get(1)

    private fun isOnPath(folder: String): Boolean {
        val wanted = normalize(folder)
        return pathFolders().any { normalize(it) == wanted }
    }

    private fun normalize(folder: String): String {
        val trimmed = folder.trimEnd('/', '\\')
        return if (os == HostOs.WINDOWS) trimmed.replace('/', '\\').lowercase() else trimmed
    }

    private fun folderOf(path: String): String = path.substringBeforeLast(separator)

    private companion object {
        const val AppLauncherName = "ShogunAi"
        const val ShimMarker = "app.luxion.shogunai.MainKt"
    }
}
