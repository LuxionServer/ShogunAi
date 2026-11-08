package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateTest {

    private val release = PublishedRelease(
        version = AppVersion(1, 3, 0),
        pageUrl = "https://example.test/releases/1.3.0",
        assets = listOf(
            ReleaseAsset("ShogunAi-1.3.0-arm64.dmg", "https://example.test/arm64.dmg"),
            ReleaseAsset("ShogunAi-1.3.0-x64.dmg", "https://example.test/x64.dmg"),
            ReleaseAsset("ShogunAi-1.3.0.msi", "https://example.test/app.msi"),
            ReleaseAsset("shogunai_1.3.0-1_amd64.deb", "https://example.test/app.deb"),
            ReleaseAsset("SHA256SUMS", "https://example.test/sums"),
        ),
    )

    @Test
    fun `parses tags with and without the v prefix`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion.DEVELOPMENT, AppVersion.parse("0.0.0"))
    }

    @Test
    fun `ignores tags that are not X Y Z`() {
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("v1.2.3-beta"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("99999999999.0.0"))
    }

    @Test
    fun `compares numerically and not alphabetically`() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 9))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertTrue(AppVersion(1, 2, 4) > AppVersion(1, 2, 3))
        assertEquals(0, AppVersion(1, 2, 3).compareTo(AppVersion(1, 2, 3)))
    }

    @Test
    fun `maps the architecture name`() {
        assertEquals(HostArchitecture.ARM64, HostArchitecture.of("aarch64"))
        assertEquals(HostArchitecture.X64, HostArchitecture.of("amd64"))
    }

    @Test
    fun `Homebrew reminds the cask upgrade`() {
        assertEquals(
            UpdateAction.RunCommand("brew upgrade --cask shogunai"),
            InstallChannel.HOMEBREW.updateAction(release, HostArchitecture.ARM64),
        )
    }

    @Test
    fun `Scoop and winget remind their commands`() {
        assertEquals(UpdateAction.RunCommand("scoop update shogunai"), InstallChannel.SCOOP.updateAction(release, HostArchitecture.X64))
        assertEquals(
            UpdateAction.RunCommand("winget upgrade LuxionServer.ShogunAi"),
            InstallChannel.WINGET.updateAction(release, HostArchitecture.X64),
        )
    }

    @Test
    fun `the dmg follows the running architecture`() {
        assertEquals(
            UpdateAction.Download(release.assets[0], null),
            InstallChannel.DMG.updateAction(release, HostArchitecture.ARM64),
        )
        assertEquals(
            UpdateAction.Download(release.assets[1], null),
            InstallChannel.DMG.updateAction(release, HostArchitecture.X64),
        )
    }

    @Test
    fun `the msi is offered for download`() {
        assertEquals(UpdateAction.Download(release.assets[2], null), InstallChannel.MSI.updateAction(release, HostArchitecture.X64))
    }

    @Test
    fun `the deb comes with the apt command`() {
        assertEquals(
            UpdateAction.Download(release.assets[3], "sudo apt install ./shogunai_1.3.0-1_amd64.deb"),
            InstallChannel.DEB.updateAction(release, HostArchitecture.X64),
        )
    }

    @Test
    fun `an unknown channel or a missing asset offers the release page`() {
        val page = UpdateAction.OpenReleasePage(release.pageUrl)

        assertEquals(page, InstallChannel.UNKNOWN.updateAction(release, HostArchitecture.X64))
        assertEquals(page, InstallChannel.MSI.updateAction(release.copy(assets = emptyList()), HostArchitecture.X64))
        assertEquals(page, InstallChannel.DEB.updateAction(release.copy(assets = emptyList()), HostArchitecture.X64))
        assertEquals(page, InstallChannel.DMG.updateAction(release.copy(assets = emptyList()), HostArchitecture.ARM64))
    }
    @Test
    fun `a version part too large for a number isn't a version`() {
        assertNull(AppVersion.parse("1.99999999999.0"))
    }

    @Test
    fun `arm64 is recognised in either spelling and anything else is x64`() {
        assertEquals(HostArchitecture.ARM64, HostArchitecture.of("ARM64"))
        assertEquals(HostArchitecture.X64, HostArchitecture.of("amd64"))
    }
}
