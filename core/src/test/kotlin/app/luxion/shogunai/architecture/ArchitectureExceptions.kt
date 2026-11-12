package app.luxion.shogunai.architecture

private fun domain(path: String): String = "$DOMAIN_SOURCES/$path"

private fun core(path: String): String = "core/src/main/kotlin/app/luxion/shogunai/$path"

private fun cli(path: String): String = "cli/src/main/kotlin/app/luxion/shogunai/cli/$path"

private fun desktop(path: String): String = "desktopApp/src/main/kotlin/app/luxion/shogunai/$path"

val DOMAIN_SYSTEM_ACCESS_EXCEPTIONS: Map<String, Set<String>> = mapOf(
    domain("model/Multirepo.kt") to setOf("java.nio.file"),
    domain("model/RepositoryConfigRules.kt") to setOf("java.nio.file"),
    domain("model/SameDirectory.kt") to setOf("java.nio.file", "System.getProperty"),
    domain("model/Tools.kt") to setOf("System.getProperty"),
    domain("model/Update.kt") to setOf("System.getProperty"),
    domain("usecase/CopyWorktreeLaunchCommandUseCase.kt") to setOf("System.getProperty"),
    domain("usecase/DetectRunningAgentsUseCase.kt") to setOf("java.io.File"),
    domain("usecase/DiagnosticReport.kt") to setOf("System.getProperty"),
    domain("usecase/IosSimulatorContribution.kt") to setOf("System.getProperty"),
    domain("usecase/OrcaWorktreeReader.kt") to setOf("java.nio.file"),
    domain("usecase/ResolveMultirepoUseCase.kt") to setOf("java.nio.file"),
    domain("usecase/ResolveRepositoryUseCase.kt") to setOf("java.nio.file"),
    domain("usecase/WorktreeEnvironmentCache.kt") to setOf("java.io.File"),
)

val ARGV_EXCEPTIONS: Set<String> = setOf(
    core("infrastructure/EditorLaunchers.kt"),
    core("infrastructure/PowerShellUserPath.kt"),
    core("infrastructure/WindowsTerminalLauncher.kt"),
)

val FILE_LENGTH_CAPS: Map<String, Int> = mapOf(
    cli("CliCommand.kt") to 1090,
    cli("CommandJson.kt") to 1028,
    cli("ShogunCli.kt") to 3492,
    core("domain/model/WorktreeError.kt") to 461,
    core("domain/usecase/WorktreeCreation.kt") to 440,
    core("infrastructure/RepositoryProvisioningFiles.kt") to 459,
    desktop("AppContainer.kt") to 1338,
    desktop("main.kt") to 453,
    desktop("ui/i18n/EnglishErrors.kt") to 479,
    desktop("ui/i18n/EnglishStrings.kt") to 1129,
    desktop("ui/i18n/SpanishErrors.kt") to 500,
    desktop("ui/i18n/SpanishStrings.kt") to 1127,
    desktop("ui/i18n/Strings.kt") to 956,
    desktop("ui/machine/MachineResources.kt") to 1006,
    desktop("ui/projectconfig/ProjectConfigViewModel.kt") to 820,
    desktop("ui/projectconfig/ProjectSettingsView.kt") to 841,
    desktop("ui/shell/AppShell.kt") to 586,
    desktop("ui/shell/ShellViewModel.kt") to 414,
    desktop("ui/shell/Sidebar.kt") to 620,
    desktop("ui/worktree/ProjectWorktrees.kt") to 1366,
    desktop("ui/worktree/WorktreeBoard.kt") to 1294,
    desktop("ui/worktree/WorktreeListPane.kt") to 828,
    desktop("ui/worktree/WorktreeRow.kt") to 409,
    desktop("ui/worktree/WorktreeScreen.kt") to 481,
)
