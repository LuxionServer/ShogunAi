package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException

sealed interface RepositoryProvisioningState {
    data object None : RepositoryProvisioningState
    data class Declared(val provisioning: RepositoryProvisioning) : RepositoryProvisioningState
    data class Unreadable(val path: String, val detail: String?) : RepositoryProvisioningState
}

internal fun RepositoryProvisioningSource.stateFor(repositoryPath: String): RepositoryProvisioningState {
    if (repositoryPath.isBlank()) return RepositoryProvisioningState.None
    return try {
        val provisioning = read(repositoryPath)
        if (provisioning.isEmpty) RepositoryProvisioningState.None else RepositoryProvisioningState.Declared(provisioning)
    } catch (error: WorktreeError.InvalidRepositoryConfig) {
        RepositoryProvisioningState.Unreadable(error.path, error.detail)
    } catch (_: IOException) {
        RepositoryProvisioningState.None
    } catch (_: InvalidPathException) {
        RepositoryProvisioningState.None
    }
}

internal fun isInsideRepository(localFile: String): Boolean {
    val normalized = localFile.replace('\\', '/')
    return !File(localFile).isAbsolute && !normalized.startsWith("/") && normalized != ".." && !normalized.startsWith("../")
}

internal fun anchoredPattern(localFile: String): String = "/" + localFile.replace('\\', '/').removePrefix("./")
