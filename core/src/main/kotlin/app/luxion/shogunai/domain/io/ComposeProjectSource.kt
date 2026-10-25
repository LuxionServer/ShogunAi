package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.ComposeResource

interface ComposeProjectSource {

    suspend fun list(): List<ComposeProject>

    suspend fun down(name: String, removeVolumes: Boolean): Result<Unit>

    suspend fun run(
        project: String,
        action: ComposeAction,
        workingDirectory: String,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit = {},
    ): Result<Unit>

    suspend fun listResources(): List<ComposeResource>

    suspend fun remove(resource: ComposeResource): Result<Unit>
}
