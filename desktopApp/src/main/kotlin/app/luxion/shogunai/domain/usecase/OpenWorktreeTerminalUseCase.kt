package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.TerminalEmulatorDetector
import app.luxion.shogunai.domain.executor.TerminalLauncher
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.TerminalSelectionMode
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Abre una terminal en la ruta de un worktree y lanza en ella el comando de
 * agente configurado para el proyecto.
 */
class OpenWorktreeTerminalUseCase(
    private val config: ProjectConfig,
    private val launcher: TerminalLauncher,
    private val detector: TerminalEmulatorDetector,
    private val commandBuilder: TerminalCommandBuilder = TerminalCommandBuilder,
) {
    suspend operator fun invoke(worktreePath: String): Result<Unit> = runCatching {
        val command = config.agentLaunchConfig.resolvedCommand()
        val preference = config.terminalPreference

        val argv = when (preference.mode) {
            TerminalSelectionMode.FIXED -> {
                val emulator = preference.emulator ?: throw WorktreeError.NoTerminalAvailable
                if (emulator !in detector.detectAvailable()) throw WorktreeError.NoTerminalAvailable
                commandBuilder.build(emulator, worktreePath, command)
            }

            TerminalSelectionMode.CUSTOM -> {
                val template = preference.customCommandTemplate ?: throw WorktreeError.NoTerminalAvailable
                commandBuilder.buildCustom(template, worktreePath, command)
            }

            TerminalSelectionMode.AUTO_DETECT -> {
                val emulator = detector.detectAvailable().firstOrNull() ?: throw WorktreeError.NoTerminalAvailable
                commandBuilder.build(emulator, worktreePath, command)
            }
        }

        launcher.launch(argv, worktreePath)
            .getOrElse { throw WorktreeError.TerminalLaunchFailed(it) }
    }
}
