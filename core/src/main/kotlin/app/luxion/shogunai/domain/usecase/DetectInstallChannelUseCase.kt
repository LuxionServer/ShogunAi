package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallChannel
import app.luxion.shogunai.domain.model.UpdateCommands

class DetectInstallChannelUseCase(
    private val os: HostOs,
    private val appLocation: String,
    private val pathExists: (String) -> Boolean,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): InstallChannel = when (os) {
        HostOs.MACOS -> if (installedWithHomebrew()) InstallChannel.HOMEBREW else InstallChannel.DMG
        HostOs.WINDOWS -> when {
            installedWithScoop() -> InstallChannel.SCOOP
            listedByWinget() -> InstallChannel.WINGET
            else -> InstallChannel.MSI
        }
        HostOs.LINUX -> if (appLocation.startsWith(LinuxPackageRoot)) InstallChannel.DEB else InstallChannel.UNKNOWN
    }

    private fun installedWithHomebrew() = HomebrewCaskrooms.any { pathExists("$it/shogunai") }

    private fun installedWithScoop() =
        appLocation.replace('/', '\\').contains("\\scoop\\apps\\shogunai\\", ignoreCase = true)

    private suspend fun listedByWinget(): Boolean = runCatching {
        val listing = executor.executeWithin(
            command = listOf("winget", "list", "--id", UpdateCommands.WINGET_ID, "--exact"),
            timeout = CommandTimeouts.LISTING,
        )
        listing.isSuccess && listing.stdout.contains(UpdateCommands.WINGET_ID, ignoreCase = true)
    }.getOrDefault(false)

    private companion object {
        val HomebrewCaskrooms = listOf("/opt/homebrew/Caskroom", "/usr/local/Caskroom")
        const val LinuxPackageRoot = "/opt/shogunai"
    }
}
