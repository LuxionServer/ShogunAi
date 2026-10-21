package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.TerminalId

internal data class MacApp(val bundleIds: List<String>, val bundleNames: List<String>)

internal object ToolCatalog {

    val terminalApps = mapOf(
        TerminalId.TERMINAL to MacApp(listOf("com.apple.Terminal"), listOf("Terminal.app", "Utilities/Terminal.app")),
        TerminalId.ITERM2 to MacApp(listOf("com.googlecode.iterm2"), listOf("iTerm.app")),
    )

    val editorApps = mapOf(
        EditorId.VS_CODE to MacApp(listOf("com.microsoft.VSCode"), listOf("Visual Studio Code.app")),
        EditorId.INTELLIJ_IDEA to MacApp(
            listOf("com.jetbrains.intellij", "com.jetbrains.intellij.ce"),
            listOf("IntelliJ IDEA.app", "IntelliJ IDEA CE.app"),
        ),
        EditorId.ANDROID_STUDIO to MacApp(listOf("com.google.android.studio"), listOf("Android Studio.app")),
        EditorId.XCODE to MacApp(listOf("com.apple.dt.Xcode"), listOf("Xcode.app")),
    )

    val claudeDesktopApp = MacApp(listOf("com.anthropic.claudefordesktop"), listOf("Claude.app"))

    val applicationFolders = listOf("/Applications", "/System/Applications", "~/Applications")

    fun editorExecutable(editor: EditorId, os: HostOs): String? {
        val name = when (editor) {
            EditorId.VS_CODE -> "code"
            EditorId.INTELLIJ_IDEA -> "idea"
            EditorId.ANDROID_STUDIO -> "studio"
            EditorId.XCODE -> return null
        }
        return if (os == HostOs.WINDOWS) "$name.cmd" else name
    }

    fun windowsTerminalExecutable(): String = "wt.exe"

    fun multiplexerExecutable(terminal: TerminalId): String = when (terminal) {
        TerminalId.TMUX -> "tmux"
        TerminalId.ZELLIJ -> "zellij"
        else -> error("${terminal.displayName} isn't a terminal multiplexer")
    }

    fun toolboxScriptFolders(os: HostOs, home: String, localAppData: String?): List<String> = when (os) {
        HostOs.MACOS -> listOf("$home/Library/Application Support/JetBrains/Toolbox/scripts")
        HostOs.WINDOWS -> listOfNotNull(localAppData?.let { "$it\\JetBrains\\Toolbox\\scripts" })
        HostOs.LINUX -> listOf("$home/.local/share/JetBrains/Toolbox/scripts")
    }

    fun userBinFolders(os: HostOs, home: String): List<String> = when (os) {
        HostOs.WINDOWS -> emptyList()
        else -> listOf("/opt/homebrew/bin", "/usr/local/bin", "$home/.local/bin", "$home/.cargo/bin")
    }
}
