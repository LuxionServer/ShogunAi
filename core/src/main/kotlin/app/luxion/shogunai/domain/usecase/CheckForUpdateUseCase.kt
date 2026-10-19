package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ReleaseSource
import app.luxion.shogunai.domain.io.UpdatePreferenceRepository
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.UpdateCheckMode
import app.luxion.shogunai.domain.model.updateAction

class CheckForUpdateUseCase(
    private val source: ReleaseSource,
    private val preferences: UpdatePreferenceRepository,
    private val detectChannel: DetectInstallChannelUseCase,
    runningVersion: String,
    private val architecture: HostArchitecture,
) {
    private val running: AppVersion = AppVersion.parse(runningVersion) ?: AppVersion.DEVELOPMENT

    suspend operator fun invoke(mode: UpdateCheckMode): Result<UpdateCheck> {
        val settings = preferences.load()
        if (mode == UpdateCheckMode.AUTOMATIC && !(settings.checkAutomatically && running != AppVersion.DEVELOPMENT)) {
            return Result.success(UpdateCheck.NotDue)
        }
        return source.latest().mapCatching { release ->
            val skipped = mode == UpdateCheckMode.AUTOMATIC && settings.skippedVersion?.let { release.version <= it } == true
            if (release.version <= running || skipped) {
                UpdateCheck.UpToDate(running)
            } else {
                val channel = detectChannel()
                UpdateCheck.Available(running, release, channel, channel.updateAction(release, architecture))
            }
        }
    }
}
