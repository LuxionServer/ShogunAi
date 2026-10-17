package app.luxion.shogunai.ui.worktree

import java.util.Locale

private val UNITS = listOf("B", "KB", "MB", "GB", "TB")
private const val STEP = 1024.0

fun formatSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= STEP && unit < UNITS.lastIndex) {
        value /= STEP
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit])
}
