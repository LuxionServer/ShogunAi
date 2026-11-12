package app.luxion.shogunai.domain.model

data class HerdrWorktreeOpening(val workspaceId: String, val rootPaneId: String, val alreadyOpen: Boolean)

data class HerdrAgent(
    val name: String,
    val state: AgentState?,
    val directory: String,
    val paneId: String,
)

const val AGENT_START_SECONDS = 10

enum class HerdrOutcome { OPENED, STARTED_AGENT, FOCUSED }

data class HerdrOpening(val outcome: HerdrOutcome, val paneId: String, val agent: String?, val sessionName: String? = null)

data class HerdrWorkspace(val workspaceId: String, val label: String, val worktreePath: String?)

data class HerdrPane(
    val paneId: String,
    val workspaceId: String,
    val directory: String?,
    val agent: String? = null,
    val agentStatus: String = UNKNOWN_AGENT_STATUS,
) {
    val hasLiveAgent: Boolean get() = agentStatus != UNKNOWN_AGENT_STATUS

    companion object {
        const val UNKNOWN_AGENT_STATUS = "unknown"
    }
}

data class HerdrPaneProcesses(val shellPid: Long?, val foregroundProcessGroupId: Long?) {
    val atPrompt: Boolean get() = shellPid != null && shellPid == foregroundProcessGroupId
}

data class HerdrPlugin(
    val id: String,
    val enabled: Boolean,
    val sourceKind: String?,
    val root: String?,
    val warnings: List<String> = emptyList(),
) {
    val isLinked: Boolean get() = sourceKind == LOCAL_SOURCE

    companion object {
        const val LOCAL_SOURCE = "local"
        const val SHOGUN_PLUGIN_ID = "shogunai.worktrees"
        const val SHOGUN_PLUGIN_SOURCE = "LuxionServer/ShogunAi/integrations/herdr"
    }
}

enum class HerdrPluginState { INSTALLED, DISABLED, BROKEN, MISSING, UNKNOWN }

data class HerdrPluginStatus(val state: HerdrPluginState, val plugin: HerdrPlugin? = null, val error: String? = null)

sealed interface HerdrPluginStep {
    val arguments: List<String>

    data class Unlink(val pluginId: String) : HerdrPluginStep {
        override val arguments: List<String> get() = listOf("plugin", "unlink", pluginId)
    }

    data class Uninstall(val pluginId: String) : HerdrPluginStep {
        override val arguments: List<String> get() = listOf("plugin", "uninstall", pluginId)
    }

    data class Install(val source: String) : HerdrPluginStep {
        override val arguments: List<String> get() = listOf("plugin", "install", source, "--yes")
    }
}
