package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.chooseAvd
import app.luxion.shogunai.domain.model.emulatorSerial
import kotlinx.coroutines.delay

class StartWorktreeEmulatorUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val acquire: AcquireWorktreeResourceUseCase,
    private val sdk: AndroidSdk,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val timeoutMs: Long = BOOT_TIMEOUT_MS,
) {
    suspend operator fun invoke(
        config: ProjectConfig,
        worktreePath: String,
        avd: String? = null,
        noWindow: Boolean? = null,
        wait: DeviceWait? = null,
    ): Result<DeviceStart<WorktreeEmulator>> = runCatching {
        val provider = emulatorProvider(provisioningSource, config)
        val location = sdk.locate(config.baseRepositoryPath)
        val acquisition = acquire.emulator(config.baseRepositoryPath, worktreePath, wait).getOrThrow()
        val environment = resolveEnvironment(config.baseRepositoryPath, worktreePath, reserve = false).getOrThrow()
        val port = environment.ports.getValue(EMULATOR_PORT_KEY)
        val serial = emulatorSerial(port)
        val current = sdk.deviceStates(location)[serial]
        if (current == EmulatorState.RUNNING) {
            return@runCatching DeviceStart(WorktreeEmulator(serial, EmulatorState.RUNNING), acquisition)
        }
        val process = if (current == null) {
            val chosen = chooseAvd(avd, config.emulator, provider.preferredAvd, sdk.listAvds(location))
                ?: throw WorktreeError.NoAvdAvailable
            sdk.startEmulator(location, chosen, port, noWindow ?: config.emulator.noWindow)
        } else {
            null
        }
        var waited = 0L
        while (sdk.deviceStates(location)[serial] != EmulatorState.RUNNING) {
            if (process != null && !process.isAlive) {
                throw WorktreeError.EmulatorExitedEarly(serial, process.lastLogLines(LOG_TAIL_LINES))
            }
            if (waited >= timeoutMs) throw WorktreeError.EmulatorBootTimeout(serial, timeoutMs / 1000)
            pause(pollIntervalMs)
            waited += pollIntervalMs
        }
        DeviceStart(WorktreeEmulator(serial, EmulatorState.RUNNING), acquisition)
    }

    companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val BOOT_TIMEOUT_MS = 180_000L
        const val LOG_TAIL_LINES = 20
    }
}

class StopWorktreeEmulatorUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val sdk: AndroidSdk,
) {
    suspend operator fun invoke(config: ProjectConfig, worktreePath: String): Result<WorktreeEmulator?> = runCatching {
        emulatorProvider(provisioningSource, config)
        val port = resolveEnvironment(config.baseRepositoryPath, worktreePath, reserve = false).getOrThrow()
            .ports[EMULATOR_PORT_KEY] ?: return@runCatching null
        stopSerials(config, listOf(emulatorSerial(port))).single()
    }

    suspend fun stopSerials(config: ProjectConfig, serials: List<String>): List<WorktreeEmulator> {
        if (serials.isEmpty()) return emptyList()
        val location = sdk.locate(config.baseRepositoryPath)
        val running = sdk.deviceStates(location)
        return serials.map { serial ->
            if (serial in running) sdk.stopEmulator(location, serial)
            WorktreeEmulator(serial, EmulatorState.STOPPED)
        }
    }
}

class ReadEmulatorStatesUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val sdk: AndroidSdk,
) {
    suspend operator fun invoke(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, WorktreeEmulator>> =
        runCatching {
            if (provisioningSource.read(config.baseRepositoryPath).providers.emulator == null) return@runCatching emptyMap()
            val serials = worktrees.filterNot { it.isPrunable }.mapNotNull { worktree ->
                resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false).getOrNull()
                    ?.ports?.get(EMULATOR_PORT_KEY)
                    ?.let { worktree.path to emulatorSerial(it) }
            }.toMap()
            if (serials.isEmpty()) return@runCatching emptyMap()
            val states = sdk.deviceStates(sdk.locate(config.baseRepositoryPath))
            serials.mapValues { (_, serial) -> WorktreeEmulator(serial, states[serial] ?: EmulatorState.STOPPED) }
        }

    suspend fun runningSerial(config: ProjectConfig, worktreePath: String): String? =
        invoke(config, listOf(Worktree(worktreePath, branch = null))).getOrNull()
            ?.get(worktreePath)
            ?.takeIf { it.state == EmulatorState.RUNNING }
            ?.serial
}

private fun emulatorProvider(provisioningSource: RepositoryProvisioningSource, config: ProjectConfig): EmulatorProvider =
    provisioningSource.read(config.baseRepositoryPath).providers.emulator ?: throw WorktreeError.EmulatorProviderNotDeclared
