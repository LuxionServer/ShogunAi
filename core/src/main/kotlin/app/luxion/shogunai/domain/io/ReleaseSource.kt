package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.PublishedRelease

interface ReleaseSource {
    suspend fun published(): Result<List<PublishedRelease>>
}
