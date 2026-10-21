package app.luxion.shogunai.ui.worktree

enum class OpenKind { TERMINAL, EDITOR, CLAUDE_DESKTOP, FOLDER }

data class OpenAction(val kind: OpenKind, val label: String, val onClick: () -> Unit)
