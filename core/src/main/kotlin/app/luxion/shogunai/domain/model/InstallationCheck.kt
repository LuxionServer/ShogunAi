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
}
