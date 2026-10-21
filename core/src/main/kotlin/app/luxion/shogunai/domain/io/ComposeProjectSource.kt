package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ComposeProject

interface ComposeProjectSource {

    suspend fun list(): List<ComposeProject>

    suspend fun down(name: String, removeVolumes: Boolean): Result<Unit>
}
