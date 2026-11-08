package app.luxion.shogunai.domain.model

data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val DEVELOPMENT = AppVersion(0, 0, 0)

        private val Pattern = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

        fun parse(raw: String): AppVersion? {
            val parts = (Pattern.matchEntire(raw.trim()) ?: return null).destructured
            val (major, minor, patch) = parts.toList().map { it.toIntOrNull() ?: return null }
            return AppVersion(major, minor, patch)
        }
    }
}

enum class HostArchitecture {
    ARM64,
    X64;

    companion object {
        fun of(architecture: String): HostArchitecture =
            if (architecture.equals("aarch64", ignoreCase = true) || architecture.equals("arm64", ignoreCase = true)) ARM64 else X64

        fun current(): HostArchitecture = of(System.getProperty("os.arch", ""))
    }
}

data class ReleaseAsset(val name: String, val downloadUrl: String)

data class PublishedRelease(
    val version: AppVersion,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
    val notes: ReleaseNotes = ReleaseNotes.NONE,
) {
    fun versionNotes(): VersionNotes = VersionNotes(version, pageUrl, notes)
}

enum class InstallChannel {
    HOMEBREW,
    DMG,
    SCOOP,
    WINGET,
    MSI,
    DEB,
    UNKNOWN,
}

sealed interface UpdateAction {
    data class RunCommand(val command: String) : UpdateAction
    data class Download(val asset: ReleaseAsset, val installCommand: String?) : UpdateAction
    data class OpenReleasePage(val url: String) : UpdateAction
}

object UpdateCommands {
    const val HOMEBREW = "brew upgrade --cask shogunai"
    const val SCOOP = "scoop update shogunai"
    const val WINGET_ID = "LuxionServer.ShogunAi"
    const val WINGET = "winget upgrade $WINGET_ID"

    fun installDeb(assetName: String) = "sudo apt install ./$assetName"
}

fun InstallChannel.updateAction(release: PublishedRelease, architecture: HostArchitecture): UpdateAction {
    val releasePage = UpdateAction.OpenReleasePage(release.pageUrl)
    fun download(asset: ReleaseAsset?, installCommand: String? = null): UpdateAction =
        asset?.let { UpdateAction.Download(it, installCommand) } ?: releasePage
    return when (this) {
        InstallChannel.HOMEBREW -> UpdateAction.RunCommand(UpdateCommands.HOMEBREW)
        InstallChannel.SCOOP -> UpdateAction.RunCommand(UpdateCommands.SCOOP)
        InstallChannel.WINGET -> UpdateAction.RunCommand(UpdateCommands.WINGET)
        InstallChannel.DMG -> {
            val suffix = if (architecture == HostArchitecture.ARM64) "-arm64.dmg" else "-x64.dmg"
            download(release.assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) })
        }
        InstallChannel.MSI -> download(release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) })
        InstallChannel.DEB -> {
            val asset = release.assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
            download(asset, asset?.let { UpdateCommands.installDeb(it.name) })
        }
        InstallChannel.UNKNOWN -> releasePage
    }
}

data class UpdatePreferences(
    val checkAutomatically: Boolean = true,
    val skippedVersion: AppVersion? = null,
)

enum class UpdateCheckMode {
    AUTOMATIC,
    MANUAL,
}

sealed interface UpdateCheck {
    data object NotDue : UpdateCheck
    data class UpToDate(val running: AppVersion) : UpdateCheck
    data class Available(
        val running: AppVersion,
        val release: PublishedRelease,
        val channel: InstallChannel,
        val action: UpdateAction,
        val notes: List<VersionNotes>,
    ) : UpdateCheck
}
