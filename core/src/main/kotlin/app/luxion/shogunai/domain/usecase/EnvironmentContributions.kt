package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.ANDROID_SERIAL
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.FIRST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.LAST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.emulatorSerial

data class PortRequest(
    val key: String,
    val candidates: IntProgression,
    val alsoFree: (Int) -> List<Int> = { emptyList() },
    val onDemand: Boolean = false,
    val exhausted: () -> WorktreeError,
)

data class ResourceContext(val repositoryFolder: String, val worktreeFolder: String)

data class ResourceRequest(
    val key: String,
    val exists: suspend (String) -> Boolean,
    val create: suspend (ResourceContext) -> String,
    val discard: suspend (String) -> Unit,
    val onDemand: Boolean = false,
)

data class EnvironmentReservations(
    val ports: Map<String, Int> = emptyMap(),
    val resources: Map<String, String> = emptyMap(),
)

data class EnvironmentContribution(
    val ports: List<PortRequest> = emptyList(),
    val resources: List<ResourceRequest> = emptyList(),
    val variables: (EnvironmentReservations) -> List<Pair<String, String>>,
) {
    fun isAcquiredIn(reservations: EnvironmentReservations): Boolean =
        ports.all { it.key in reservations.ports } && resources.all { it.key in reservations.resources }
}

fun interface ProviderContributions {
    fun forRepository(repositoryPath: String, providers: RepositoryProviders): List<EnvironmentContribution>

    companion object {
        val DEFAULT = ProviderContributions { _, providers -> listOfNotNull(providers.emulator?.let { emulatorContribution }) }
    }
}

const val FIRST_VARIABLE_PORT = 20000
const val LAST_VARIABLE_PORT = 29999

fun declaredPortRequests(declaration: EnvironmentDeclaration): List<PortRequest> =
    declaration.portKeys.map { key ->
        PortRequest(key, FIRST_VARIABLE_PORT..LAST_VARIABLE_PORT) {
            WorktreeError.NoFreePort(FIRST_VARIABLE_PORT, LAST_VARIABLE_PORT)
        }
    }

val emulatorContribution = EnvironmentContribution(
    ports = listOf(
        PortRequest(
            key = EMULATOR_PORT_KEY,
            candidates = FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT step 2,
            alsoFree = { listOf(it + 1) },
            exhausted = { WorktreeError.NoEmulatorPort(FIRST_EMULATOR_PORT, LAST_EMULATOR_PORT) },
            onDemand = true,
        ),
    ),
    variables = { reservations -> listOf(ANDROID_SERIAL to emulatorSerial(reservations.ports.getValue(EMULATOR_PORT_KEY))) },
)
