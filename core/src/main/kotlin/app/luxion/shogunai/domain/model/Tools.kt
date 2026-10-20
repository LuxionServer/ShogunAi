package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

enum class HostOs {
    MACOS,
    WINDOWS,
    LINUX;

    companion object {
        fun of(osName: String): HostOs = when {
            osName.startsWith("Mac", ignoreCase = true) -> MACOS
            osName.startsWith("Windows", ignoreCase = true) -> WINDOWS
            else -> LINUX
        }

        fun current(): HostOs = of(System.getProperty("os.name").orEmpty())
    }
}

@Serializable
enum class TerminalId(val displayName: String, val usesPowerShell: Boolean = false) {
    TERMINAL("Terminal"),
    ITERM2("iTerm2"),
    WINDOWS_TERMINAL("Windows Terminal", usesPowerShell = true),
    TMUX("tmux"),
    ZELLIJ("Zellij"),
}

@Serializable
enum class EditorId(val displayName: String) {
    VS_CODE("VS Code"),
    INTELLIJ_IDEA("IntelliJ IDEA"),
    ANDROID_STUDIO("Android Studio"),
    XCODE("Xcode"),
}

data class TerminalLaunch(
    val worktreePath: String,
    val variables: List<Pair<String, String>>,
    val agentCommand: String,
    val line: String,
)

data class DetectedTools(
    val terminals: List<TerminalId> = emptyList(),
    val editors: List<EditorId> = emptyList(),
)
