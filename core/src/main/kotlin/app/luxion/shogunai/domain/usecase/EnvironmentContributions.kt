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
    val exhausted: () -> WorktreeError,
)

data class EnvironmentContribution(
    val ports: List<PortRequest>,
    val variables: (Map<String, Int>) -> List<Pair<String, String>>,
)

const val FIRST_VARIABLE_PORT = 20000
const val LAST_VARIABLE_PORT = 29999

fun declaredPortRequests(declaration: EnvironmentDeclaration): List<PortRequest> =
    declaration.portKeys.map { key ->
        PortRequest(key, FIRST_VARIABLE_PORT..LAST_VARIABLE_PORT) {
            WorktreeError.NoFreePort(FIRST_VARIABLE_PORT, LAST_VARIABLE_PORT)
        }
    }

fun providerContributions(providers: RepositoryProviders): List<EnvironmentContribution> = listOfNotNull(
    providers.emulator?.let { emulatorContribution },
)

private val emulatorContribution = EnvironmentContribution(
    ports = listOf(
        PortRequest(
            key = EMULATOR_PORT_KEY,
            candidates = FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT step 2,
            alsoFree = { listOf(it + 1) },
            exhausted = { WorktreeError.NoEmulatorPort(FIRST_EMULATOR_PORT, LAST_EMULATOR_PORT) },
        ),
    ),
    variables = { ports -> listOf(ANDROID_SERIAL to emulatorSerial(ports.getValue(EMULATOR_PORT_KEY))) },
)
