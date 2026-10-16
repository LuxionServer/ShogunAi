package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_UDID
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.chooseSimulator
import app.luxion.shogunai.domain.model.worktreeSimulatorName

class MachineProviderContributions(
    private val simulators: AppleSimulators,
    private val simulatorSettingsFor: (repositoryPath: String) -> SimulatorSettings,
    private val requestedSimulator: SimulatorSettings = SimulatorSettings(),
) : ProviderContributions {

    override fun forRepository(repositoryPath: String, providers: RepositoryProviders): List<EnvironmentContribution> =
        listOfNotNull(
            providers.emulator?.let { emulatorContribution },
            providers.iosSimulator?.takeIf { simulators.isSupported() }?.let { preferred ->
                iosSimulatorContribution(simulators, preferred, simulatorSettingsFor(repositoryPath), requestedSimulator)
            },
        )

    fun requesting(simulator: SimulatorSettings): MachineProviderContributions =
        MachineProviderContributions(simulators, simulatorSettingsFor, simulator)
}

fun iosSimulatorContribution(
    simulators: AppleSimulators,
    preferred: IosSimulatorProvider,
    settings: SimulatorSettings,
    requested: SimulatorSettings,
) = EnvironmentContribution(
    resources = listOf(
        ResourceRequest(
            key = IOS_SIMULATOR_KEY,
            exists = { udid -> simulators.devices().any { it.udid == udid } },
            create = { context ->
                val choice = chooseSimulator(requested, settings, preferred, simulators.runtimes())
                    ?: throw WorktreeError.NoIosRuntime
                simulators.create(
                    worktreeSimulatorName(context.repositoryFolder, context.worktreeFolder),
                    choice.deviceType.identifier,
                    choice.runtime.identifier,
                )
            },
            discard = { udid -> simulators.delete(udid) },
        ),
    ),
    variables = { reservations -> listOf(IOS_SIMULATOR_UDID to reservations.resources.getValue(IOS_SIMULATOR_KEY)) },
)
