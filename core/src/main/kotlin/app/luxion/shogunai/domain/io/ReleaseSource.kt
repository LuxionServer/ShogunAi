package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.LatestRelease

interface ReleaseSource {
    suspend fun latest(): Result<LatestRelease>
}
