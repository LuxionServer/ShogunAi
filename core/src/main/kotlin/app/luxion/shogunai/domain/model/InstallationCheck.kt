package app.luxion.shogunai.domain.model

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.HooksInstallState

enum class CheckState { PASSED, WARNING, FAILED, NOT_FOUND }

enum class AgentToolId(val id: String, val displayName: String, val website: String) {
    CLAUDE_CODE("claude-code", "Claude Code", "https://docs.claude.com/en/docs/claude-code/setup"),
    HERDR("herdr", "Herdr", "https://herdr.dev"),
    ORCA("orca", "Orca", "https://www.onorca.dev"),
}

enum class ProviderToolId(val id: String, val displayName: String, val provider: String) {
    PROVIDERS("providers", "Providers", ""),
    GRADLE("gradle", "Gradle", "gradle"),
    MAVEN("maven", "Maven", "maven"),
    ANDROID_SDK("android-sdk", "Android SDK", "emulator"),
    IOS_SIMULATOR("ios-simulator", "iOS Simulator", "ios-simulator"),
    XCODE("xcode", "Xcode", "xcode"),
    COCOAPODS("cocoapods", "CocoaPods", "xcode"),
    DOCKER_COMPOSE("docker-compose", "Docker Compose", "docker-compose"),
    FLUTTER("flutter", "Flutter", "flutter"),
}

sealed interface ProviderToolProblem {
    data class NotFound(val searched: List<String>, val commands: List<String> = emptyList()) : ProviderToolProblem
    data class Unusable(val detail: String?) : ProviderToolProblem
    data object NeedsMacOs : ProviderToolProblem
    data object NoAndroidVirtualDevice : ProviderToolProblem
    data object NoIosRuntime : ProviderToolProblem
    data class ProvidersUnreadable(val detail: String?) : ProviderToolProblem
}

sealed interface MultirepoProblem {
    val blocking: Boolean get() = false

    data object DeclarationMissing : MultirepoProblem

    data class InvalidDeclaration(val path: String, val detail: String?) : MultirepoProblem {
        override val blocking: Boolean get() = true
    }

    data class RepositoryMissing(val alias: String, val path: String) : MultirepoProblem {
        override val blocking: Boolean get() = true
    }

    data object NotTrusted : MultirepoProblem

    data class ListingFailed(val alias: String, val detail: String?) : MultirepoProblem

    data class GroupWorktreeMissing(val branch: String, val alias: String, val path: String) : MultirepoProblem
}

data class InstallationCheck(val state: CheckState, val finding: InstallationFinding)

data class InstallationReport(val checks: List<InstallationCheck>) {
    val failed: Boolean get() = checks.any { it.state == CheckState.FAILED }
}

sealed interface InstallationFinding {
    val id: String

    data class Git(val location: String?, val version: String?, val minimum: String, val detail: String? = null) : InstallationFinding {
        override val id: String get() = "git"
    }

    data class PathSource(val source: HostPathSource, val shell: String?) : InstallationFinding {
        override val id: String get() = "path"
    }

    data class ShogunCommand(
        val state: CommandLineToolState,
        val launcher: String?,
        val folder: String?,
        val restartNeeded: Boolean,
    ) : InstallationFinding {
        override val id: String get() = "shogun"
    }

    data class Hooks(
        val settingsPath: String,
        val state: HooksInstallState?,
        val events: List<String> = emptyList(),
        val shogun: CommandLineToolState = CommandLineToolState.AVAILABLE,
        val invalidDetail: String? = null,
    ) : InstallationFinding {
        override val id: String get() = "claude-hooks"
    }

    data class AgentTool(val tool: AgentToolId, val location: String?) : InstallationFinding {
        override val id: String get() = tool.id
    }

    data class ProjectFiles(
        val project: String,
        val basePath: String,
        val baseMissing: Boolean,
        val checked: Int,
        val unavailable: List<UnavailableLocalFile>,
        val detail: String? = null,
    ) : InstallationFinding {
        override val id: String get() = "project:$project"
    }

    data class ProviderTool(
        val project: String,
        val tool: ProviderToolId,
        val location: String?,
        val problem: ProviderToolProblem? = null,
        val installed: Int? = null,
    ) : InstallationFinding {
        override val id: String get() = "provider:$project:${tool.id}"
    }

    data class MultirepoCheck(
        val folder: String,
        val name: String,
        val repositories: List<String>,
        val groups: Int,
        val problems: List<MultirepoProblem>,
    ) : InstallationFinding {
        override val id: String get() = "multirepo:$name"
    }
}
