package app.luxion.shogunai.ui.shell

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

class RecordingClipboard : Clipboard {
    val entries = mutableListOf<ClipEntry?>()

    override suspend fun getClipEntry(): ClipEntry? = entries.lastOrNull()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        entries += clipEntry
    }
}
