package app.luxion.shogunai.domain.model

object ClaudeComputerUse {

    val prefixes: List<String> = listOf("mcp__computer-use__", "mcp__remote-devices__computer_")

    val actingTools: List<String> = listOf(
        "left_click",
        "right_click",
        "middle_click",
        "double_click",
        "triple_click",
        "mouse_move",
        "left_click_drag",
        "left_mouse_down",
        "left_mouse_up",
        "type",
        "key",
        "hold_key",
        "scroll",
        "open_application",
        "write_clipboard",
        "computer_batch",
    )

    val hookMatcher: String =
        "^(" + prefixes.joinToString("|") + ")(" + actingTools.joinToString("|") + ")$"

    fun drivesDesktop(toolName: String): Boolean =
        prefixes.any { prefix -> toolName.startsWith(prefix) && toolName.removePrefix(prefix) in actingTools }
}
