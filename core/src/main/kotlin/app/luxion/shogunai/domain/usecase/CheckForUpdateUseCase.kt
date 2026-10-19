package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ReleaseSource
import app.luxion.shogunai.domain.io.UpdatePreferenceRepository
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.UpdateCheckMode
import app.luxion.shogunai.domain.model.updateAction
import java.time.Clock
import java.time.Duration

class CheckForUpdateUseCase(
    private val source: ReleaseSource,
    private val preferences: UpdatePreferenceRepository,
    private val detectChannel: DetectInstallChannelUseCase,
    private val clock: Clock,
    runningVersion: String,
    private val architecture: HostArchitecture,
) {
    private val running: AppVersion = AppVersion.parse(runningVersion) ?: AppVersion.DEVELOPMENT

    suspend operator fun invoke(mode: UpdateCheckMode): Result<UpdateCheck> {
        val settings = preferences.load()
        if (mode == UpdateCheckMode.AUTOMATIC && !isDue(settings.checkAutomatically, settings.lastCheck)) {
            return Result.success(UpdateCheck.NotDue)
        }
        val latest = source.latest()
        preferences.recordCheck(clock.instant())
        return latest.mapCatching { release ->
            val ignored = mode == UpdateCheckMode.AUTOMATIC && settings.skippedVersion?.let { release.version <= it } == true
            if (release.version <= running || ignored) {
                UpdateCheck.UpToDate(running)
            } else {
                val channel = detectChannel()
                UpdateCheck.Available(running, release, channel, channel.updateAction(release, architecture))
            }
        }
    }

    private fun isDue(enabled: Boolean, lastCheck: java.time.Instant?): Boolean =
        enabled && running != AppVersion.DEVELOPMENT &&
            (lastCheck == null || Duration.between(lastCheck, clock.instant()) >= CheckInterval)

    private companion object {
        val CheckInterval: Duration = Duration.ofHours(24)
    }
}
