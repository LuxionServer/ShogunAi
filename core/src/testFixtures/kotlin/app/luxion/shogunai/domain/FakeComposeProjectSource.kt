package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.OutputChannel
import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.WorktreeError

class FakeComposeProjectSource(
    var projects: List<ComposeProject> = emptyList(),
    var unavailable: String? = null,
    private val failingDowns: Map<String, String> = emptyMap(),
    var resources: List<ComposeResource> = emptyList(),
    var failingRemovals: Map<String, String> = emptyMap(),
    var failingRun: String? = null,
) : ComposeProjectSource {

    val listings = mutableListOf<Unit>()
    val downs = mutableListOf<Pair<String, Boolean>>()
    val runs = mutableListOf<ComposeRun>()
    val removals = mutableListOf<ComposeResource>()

    override suspend fun list(): List<ComposeProject> {
        listings += Unit
        unavailable?.let { throw WorktreeError.DockerUnavailable(it) }
        return projects
    }

    override suspend fun down(name: String, removeVolumes: Boolean): Result<Unit> {
        downs += name to removeVolumes
        return failingDowns[name]?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }

    override suspend fun run(
        project: String,
        action: ComposeAction,
        workingDirectory: String,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): Result<Unit> {
        runs += ComposeRun(project, action, workingDirectory, environment)
        unavailable?.let { return Result.failure(WorktreeError.DockerUnavailable(it)) }
        onOutput(OutputLine(OutputChannel.ERROR, "Container $project-api Started"))
        return failingRun?.let { Result.failure(WorktreeError.ComposeCommandFailed(project, it)) } ?: Result.success(Unit)
    }

    override suspend fun listResources(): List<ComposeResource> {
        unavailable?.let { throw WorktreeError.DockerUnavailable(it) }
        return resources
    }

    override suspend fun remove(resource: ComposeResource): Result<Unit> {
        removals += resource
        failingRemovals[resource.id]?.let { return Result.failure(IllegalStateException(it)) }
        resources = resources - resource
        return Result.success(Unit)
    }
}

data class ComposeRun(
    val project: String,
    val action: ComposeAction,
    val workingDirectory: String,
    val environment: Map<String, String>,
)
