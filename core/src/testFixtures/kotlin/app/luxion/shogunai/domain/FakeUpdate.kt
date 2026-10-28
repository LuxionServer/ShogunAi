package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ReleaseSource
import app.luxion.shogunai.domain.io.UpdatePreferenceRepository
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.PublishedRelease
import app.luxion.shogunai.domain.model.UpdatePreferences

class FakeReleaseSource(
    private val responder: () -> Result<List<PublishedRelease>>,
) : ReleaseSource {

    var requests = 0
        private set

    override suspend fun published(): Result<List<PublishedRelease>> {
        requests++
        return responder()
    }
}

class FakeUpdatePreferenceRepository(
    var preferences: UpdatePreferences = UpdatePreferences(),
) : UpdatePreferenceRepository {

    override fun load(): UpdatePreferences = preferences

    override fun setCheckAutomatically(enabled: Boolean) {
        preferences = preferences.copy(checkAutomatically = enabled)
    }

    override fun skip(version: AppVersion) {
        preferences = preferences.copy(skippedVersion = version)
    }
}
