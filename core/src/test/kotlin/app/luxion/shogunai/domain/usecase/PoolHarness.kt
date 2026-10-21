package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.SimulatorSettings
import java.time.Instant

class PoolHarness(
    val git: ShellCommandExecutor,
    val source: FakeRepositoryProvisioningSource,
    val store: FakeEnvironmentStateStore = FakeEnvironmentStateStore(),
    val sdk: FakeAndroidSdk = FakeAndroidSdk(),
    val simulators: FakeAppleSimulators = FakeAppleSimulators(),
    val files: FakeFileManager = FakeFileManager(),
    val portProbe: FakePortProbe = FakePortProbe(),
    val simulatorSettings: SimulatorSettings = SimulatorSettings(),
    val daemons: FakeGradleDaemonSource = FakeGradleDaemonSource(),
) {
    val settings = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE))
    var now: Instant = Instant.parse("2026-01-01T10:00:00Z")

    fun limit(emulators: Int? = null, simulators: Int? = null, gradleDaemons: Int? = null) {
        settings.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators, simulators, gradleDaemons))
    }

    val limits get() = ResolveResourceLimitsUseCase(settings, FakeMachineCapacity())

    val trim get() = TrimGradleDaemonsUseCase(ListGradleDaemonsUseCase(daemons, files), StopGradleDaemonsUseCase(daemons, git), limits)

    val contributions = MachineProviderContributions(simulators, { simulatorSettings })

    val resolve get() = ResolveWorktreeEnvironmentUseCase(git, source, store, portProbe, contributions)

    var agentStates: Map<String, AgentState> = emptyMap()

    val readPool get() = ReadResourcePoolUseCase(
        resolve,
        store,
        files,
        sdk,
        simulators,
        limits,
        ListGradleDaemonsUseCase(daemons, files),
        agentActivity = { agentStates[it] },
        clock = { now },
    )

    val acquire get() = AcquireWorktreeResourceUseCase(
        source,
        resolve,
        WriteEnvironmentFileUseCase(git, files, source),
        store,
        portProbe,
        simulators,
        sdk,
        readPool,
        { requested -> contributions.requesting(requested) },
        clock = { now },
    )
}
