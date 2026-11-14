package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import java.time.Duration

interface JsonOutput {
    fun withJson(): CliCommand
}

sealed interface CliCommand {
    data object Help : CliCommand
    data object Version : CliCommand
    data class CommandHelp(val name: String) : CliCommand

    data class New(
        val taskId: String? = null,
        val branch: String? = null,
        val branchType: BranchType = BranchType.FEATURE,
        val worktreesRoot: String? = null,
        val from: String? = null,
        val fetch: Boolean = false,
        val json: Boolean = false,
        val repos: List<String>? = null,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }

    data class Multirepo(val action: MultirepoAction, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }

    data class Provision(val path: String? = null, val check: Boolean = false, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Trust(
        val path: String? = null,
        val yes: Boolean = false,
        val revoke: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Remove(
        val target: String,
        val force: Boolean = false,
        val deleteBranch: Boolean = false,
        val forceDeleteBranch: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Repair(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Open(
        val target: String?,
        val runner: RunnerId? = null,
        val agent: String? = null,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Status(val json: Boolean = false) : CliCommand
    data class Base(val target: String? = null, val set: String? = null, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Clean(
        val confirmed: Boolean = false,
        val deleteBranches: Boolean = false,
        val stopGradleDaemons: Boolean = false,
        val stopMavenDaemons: Boolean = false,
        val stopEmulators: Boolean = false,
        val deleteSimulators: Boolean = false,
        val deleteDerivedData: Boolean = false,
        val downComposeProjects: Boolean = false,
        val removeComposeVolumes: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }

    data class Xcode(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class XcodeClean(val swiftBuild: Boolean = false, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class XcodeTrim(val dryRun: Boolean = false, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }

    data class Gradle(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Stacks(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Doctor(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Maven(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Compose(val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class ComposeDown(val target: String? = null, val volumes: Boolean = false, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class ComposeRun(
        val target: String? = null,
        val build: Boolean = false,
        val onlyBuild: Boolean = false,
        val extraArguments: List<String> = emptyList(),
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class ComposePrune(val volumes: Boolean = false, val confirmed: Boolean = false, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Pool(val action: PoolAction, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Project(val action: ProjectAction, val path: String? = null, val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class App(val path: String? = null, val settings: Boolean = false) : CliCommand
    data class GradleStop(val pids: List<Long> = emptyList(), val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }

    data class MavenStop(val pids: List<Long> = emptyList(), val json: Boolean = false) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data object HookWorktreeCreate : CliCommand
    data object HookWorktreeRemove : CliCommand
    data object HookSessionStart : CliCommand
    data object HookHerdr : CliCommand
    data class HookOrca(val action: OrcaHookAction) : CliCommand
    data object HookAgentState : CliCommand
    data object HookDesktopTurn : CliCommand
    data class Hooks(
        val action: HooksAction,
        val project: Boolean = false,
        val yes: Boolean = false,
        val json: Boolean = false,
        val herdr: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Guide(
        val topic: String? = null,
        val plain: Boolean = false,
        val migrate: Boolean = false,
        val file: String? = null,
    ) : CliCommand
    data class GuideBlock(
        val action: GuideAction,
        val agent: String? = null,
        val project: Boolean = false,
        val file: String? = null,
        val yes: Boolean = false,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Emulator(
        val action: EmulatorAction,
        val path: String? = null,
        val avd: String? = null,
        val noWindow: Boolean = false,
        val json: Boolean = false,
        val wait: Duration? = null,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Headless(
        val action: HeadlessAction,
        val path: String? = null,
        val visible: Boolean = false,
        val fresh: Boolean = false,
        val output: String? = null,
        val json: Boolean = false,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Desktop(
        val action: DesktopAction,
        val path: String? = null,
        val json: Boolean = false,
        val wait: Duration? = null,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Device(
        val action: DeviceAction,
        val path: String? = null,
        val json: Boolean = false,
        val all: Boolean = false,
        val platform: DevicePlatform? = null,
        val name: String? = null,
        val model: String? = null,
        val wait: Duration? = null,
        val device: String? = null,
        val alias: String? = null,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class Simulator(
        val action: SimulatorAction,
        val path: String? = null,
        val device: String? = null,
        val runtime: String? = null,
        val json: Boolean = false,
        val wait: Duration? = null,
    ) : CliCommand, JsonOutput {
        override fun withJson() = copy(json = true)
    }
    data class SimulatorGradleInit(val install: Boolean, val confirmed: Boolean = false) : CliCommand
    data class Env(val path: String? = null, val format: EnvironmentFormat = EnvironmentFormat.SH) : CliCommand
}
