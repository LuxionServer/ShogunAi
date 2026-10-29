package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.Strings

fun catalogFor(
    runners: List<RunnerId> = RunnerId.entries,
    defaultRunner: RunnerId? = RunnerId.HERDR,
    terminal: String? = "iTerm2",
    editor: String? = "IntelliJ IDEA",
    claudeDesktop: Boolean = false,
    isMain: Boolean = false,
    isFolderMissing: Boolean = false,
    agents: List<String> = listOf("Claude", "Codex"),
    agent: String? = "Claude",
    opening: Set<OpenTarget> = emptySet(),
    strings: Strings = EnglishStrings,
): WorktreeCatalog = worktreeCatalog(
    WorktreeActionContext(
        tools = ActionTools(runners, defaultRunner, terminal, editor, claudeDesktop),
        isMain = isMain,
        isFolderMissing = isFolderMissing,
        agents = agents,
        agent = agent,
        opening = opening,
    ),
    strings,
)
