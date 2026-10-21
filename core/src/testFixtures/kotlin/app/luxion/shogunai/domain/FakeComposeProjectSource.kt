package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.WorktreeError

class FakeComposeProjectSource(
    var projects: List<ComposeProject> = emptyList(),
    var unavailable: String? = null,
    private val failingDowns: Map<String, String> = emptyMap(),
) : ComposeProjectSource {

    val listings = mutableListOf<Unit>()
    val downs = mutableListOf<Pair<String, Boolean>>()

    override suspend fun list(): List<ComposeProject> {
        listings += Unit
        unavailable?.let { throw WorktreeError.DockerUnavailable(it) }
        return projects
    }

    override suspend fun down(name: String, removeVolumes: Boolean): Result<Unit> {
        downs += name to removeVolumes
        return failingDowns[name]?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }
}
