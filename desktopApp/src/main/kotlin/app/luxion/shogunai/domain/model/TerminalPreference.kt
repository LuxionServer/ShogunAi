package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/** Emuladores de terminal soportados de forma nativa, en macOS y Linux. */
enum class TerminalEmulator {
    MACOS_TERMINAL,
    ITERM2,
    WARP,
    GNOME_TERMINAL,
    KONSOLE,
    XTERM,
}

/** Cómo se resuelve qué emulador de terminal usar al abrir un worktree. */
enum class TerminalSelectionMode {
    /** Se detecta automáticamente un emulador instalado en el sistema. */
    AUTO_DETECT,

    /** El usuario fija explícitamente uno de los [TerminalEmulator] soportados. */
    FIXED,

    /** El usuario da su propia plantilla de comando (argv), para cualquier emulador. */
    CUSTOM,
}

/**
 * Preferencia de terminal de un proyecto.
 *
 * @param mode Modo de selección del emulador.
 * @param emulator Emulador fijado cuando [mode] es [TerminalSelectionMode.FIXED].
 * @param customCommandTemplate Argv de plantilla cuando [mode] es
 *   [TerminalSelectionMode.CUSTOM], con los placeholders `{path}` y `{command}`
 *   sustituidos por argumento, nunca concatenados en una cadena de shell.
 */
@Serializable
data class TerminalPreference(
    val mode: TerminalSelectionMode = TerminalSelectionMode.AUTO_DETECT,
    val emulator: TerminalEmulator? = null,
    val customCommandTemplate: List<String>? = null,
)
