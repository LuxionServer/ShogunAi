package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.TerminalId

internal data class MacApp(val bundleIds: List<String>, val bundleNames: List<String>)

internal data class WindowsInstall(
    val parent: String?,
    val folder: String,
    val versioned: Boolean,
    val executables: List<String>,
)

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

    fun windowsCommandNames(editor: EditorId): List<String> = when (editor) {
        EditorId.VS_CODE -> listOf("code")
        EditorId.INTELLIJ_IDEA -> listOf("idea", "idea64")
        EditorId.ANDROID_STUDIO -> listOf("studio", "studio64")
        EditorId.XCODE -> emptyList()
    }

    fun windowsInstalls(editor: EditorId): List<WindowsInstall> = when (editor) {
        EditorId.VS_CODE -> listOf(WindowsInstall(null, "Microsoft VS Code", false, listOf("bin\\code.cmd", "Code.exe")))
        EditorId.INTELLIJ_IDEA -> listOf(WindowsInstall("JetBrains", "IntelliJ IDEA", true, listOf("bin\\idea64.exe")))
        EditorId.ANDROID_STUDIO -> listOf(
            WindowsInstall("Android", "Android Studio", true, listOf("bin\\studio64.exe")),
            WindowsInstall(null, "Android Studio", true, listOf("bin\\studio64.exe")),
        )
        EditorId.XCODE -> emptyList()
    }

    fun windowsToolboxProducts(editor: EditorId): List<String> = when (editor) {
        EditorId.INTELLIJ_IDEA -> listOf("IDEA", "intellij-idea")
        EditorId.ANDROID_STUDIO -> listOf("AndroidStudio", "android-studio")
        else -> emptyList()
    }

    fun windowsToolboxAppsFolder(localAppData: String): String = "$localAppData\\JetBrains\\Toolbox\\apps"

    fun windowsRegistryMarker(editor: EditorId): String? = when (editor) {
        EditorId.VS_CODE -> "microsoft vs code"
        EditorId.INTELLIJ_IDEA -> "intellij idea"
        EditorId.ANDROID_STUDIO -> "android studio"
        EditorId.XCODE -> null
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
