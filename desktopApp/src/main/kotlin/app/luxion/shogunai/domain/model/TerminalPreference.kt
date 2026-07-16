package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/** Terminal emulators natively supported, on macOS and Linux. */
enum class TerminalEmulator {
    MACOS_TERMINAL,
    ITERM2,
    WARP,
    GNOME_TERMINAL,
    KONSOLE,
    XTERM,
}

/** How the terminal emulator to use when opening a worktree is resolved. */
enum class TerminalSelectionMode {
    /** Automatically detects an emulator installed on the system. */
    AUTO_DETECT,

    /** The user explicitly picks one of the supported [TerminalEmulator]s. */
    FIXED,

    /** The user provides their own command template (argv), for any emulator. */
    CUSTOM,
}

/**
 * A project's terminal preference.
 *
 * @param mode Emulator selection mode.
 * @param emulator Fixed emulator when [mode] is [TerminalSelectionMode.FIXED].
 * @param customCommandTemplate Template argv when [mode] is
 *   [TerminalSelectionMode.CUSTOM], with the `{path}` and `{command}` placeholders
 *   substituted per-argument, never concatenated into a shell string.
 */
@Serializable
data class TerminalPreference(
    val mode: TerminalSelectionMode = TerminalSelectionMode.AUTO_DETECT,
    val emulator: TerminalEmulator? = null,
    val customCommandTemplate: List<String>? = null,
)
