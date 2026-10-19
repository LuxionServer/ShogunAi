package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeReleaseSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeUpdatePreferenceRepository
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallChannel
import app.luxion.shogunai.domain.model.LatestRelease
import app.luxion.shogunai.domain.model.UpdateAction
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.UpdateCheckMode
import app.luxion.shogunai.domain.model.UpdatePreferences
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckForUpdateUseCaseTest {

    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val release = LatestRelease(AppVersion(1, 3, 0), "https://example.test/releases/1.3.0", emptyList())

    private fun useCase(
        source: FakeReleaseSource,
        preferences: FakeUpdatePreferenceRepository,
        running: String = "1.2.0",
    ) = CheckForUpdateUseCase(
        source = source,
        preferences = preferences,
        detectChannel = DetectInstallChannelUseCase(HostOs.MACOS, "/app", { it == "/opt/homebrew/Caskroom/shogunai" }, FakeShellCommandExecutor()),
        clock = clock,
        runningVersion = running,
        architecture = HostArchitecture.ARM64,
    )

    private fun source(result: Result<LatestRelease> = Result.success(release)) = FakeReleaseSource { result }

    private fun preferences(
        lastCheck: Instant? = now.minus(Duration.ofDays(2)),
        enabled: Boolean = true,
        skipped: AppVersion? = null,
    ) = FakeUpdatePreferenceRepository(UpdatePreferences(enabled, lastCheck, skipped))

    @Test
    fun `announces a newer version with the channel's action and records the check`() = runTest {
        val preferences = preferences()

        val check = useCase(source(), preferences)(UpdateCheckMode.AUTOMATIC).getOrThrow()

        val available = assertIs<UpdateCheck.Available>(check)
        assertEquals(AppVersion(1, 2, 0), available.running)
        assertEquals(release, available.release)
        assertEquals(InstallChannel.HOMEBREW, available.channel)
        assertEquals(UpdateAction.RunCommand("brew upgrade --cask shogunai"), available.action)
        assertEquals(now, preferences.preferences.lastCheck)
    }

    @Test
    fun `checks when it has never checked`() = runTest {
        val source = source()

        useCase(source, preferences(lastCheck = null))(UpdateCheckMode.AUTOMATIC)

        assertEquals(1, source.requests)
    }

    @Test
    fun `makes no request when the last check was an hour ago`() = runTest {
        val source = source()

        val check = useCase(source, preferences(lastCheck = now.minus(Duration.ofHours(1))))(UpdateCheckMode.AUTOMATIC)

        assertEquals(UpdateCheck.NotDue, check.getOrThrow())
        assertEquals(0, source.requests)
    }

    @Test
    fun `checks again exactly 24 hours later`() = runTest {
        val source = source()

        useCase(source, preferences(lastCheck = now.minus(Duration.ofHours(24))))(UpdateCheckMode.AUTOMATIC)

        assertEquals(1, source.requests)
    }

    @Test
    fun `makes no automatic request when checks are off but a manual one does`() = runTest {
        val source = source()
        val preferences = preferences(enabled = false)

        assertEquals(UpdateCheck.NotDue, useCase(source, preferences)(UpdateCheckMode.AUTOMATIC).getOrThrow())
        assertEquals(0, source.requests)

        assertIs<UpdateCheck.Available>(useCase(source, preferences)(UpdateCheckMode.MANUAL).getOrThrow())
        assertEquals(1, source.requests)
    }

    @Test
    fun `development builds never check automatically`() = runTest {
        val source = source()

        val check = useCase(source, preferences(), running = "0.0.0")(UpdateCheckMode.AUTOMATIC)

        assertEquals(UpdateCheck.NotDue, check.getOrThrow())
        assertEquals(0, source.requests)
    }

    @Test
    fun `a manual check ignores the schedule`() = runTest {
        val source = source()

        useCase(source, preferences(lastCheck = now.minus(Duration.ofMinutes(1))))(UpdateCheckMode.MANUAL)

        assertEquals(1, source.requests)
    }

    @Test
    fun `reports up to date when the running version is the latest or newer`() = runTest {
        assertEquals(UpdateCheck.UpToDate(AppVersion(1, 3, 0)), useCase(source(), preferences(), "1.3.0")(UpdateCheckMode.MANUAL).getOrThrow())
        assertEquals(UpdateCheck.UpToDate(AppVersion(1, 4, 0)), useCase(source(), preferences(), "1.4.0")(UpdateCheckMode.MANUAL).getOrThrow())
    }

    @Test
    fun `a skipped version is not announced automatically but is on demand`() = runTest {
        val preferences = preferences(skipped = AppVersion(1, 3, 0))

        assertIs<UpdateCheck.UpToDate>(useCase(source(), preferences)(UpdateCheckMode.AUTOMATIC).getOrThrow())
        assertIs<UpdateCheck.Available>(useCase(source(), preferences)(UpdateCheckMode.MANUAL).getOrThrow())
    }

    @Test
    fun `a version newer than the skipped one is announced`() = runTest {
        val newer = release.copy(version = AppVersion(1, 3, 1))

        val check = useCase(source(Result.success(newer)), preferences(skipped = AppVersion(1, 3, 0)))(UpdateCheckMode.AUTOMATIC)

        assertIs<UpdateCheck.Available>(check.getOrThrow())
    }

    @Test
    fun `a failed check is returned and not retried before 24 hours`() = runTest {
        val failing = source(Result.failure(WorktreeError.UpdateCheckFailed("offline")))
        val preferences = preferences()

        val check = useCase(failing, preferences)(UpdateCheckMode.AUTOMATIC)

        assertTrue(check.exceptionOrNull() is WorktreeError.UpdateCheckFailed)
        assertEquals(now, preferences.preferences.lastCheck)
        assertEquals(UpdateCheck.NotDue, useCase(failing, preferences)(UpdateCheckMode.AUTOMATIC).getOrThrow())
        assertEquals(1, failing.requests)
    }
}
