package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class AwtClipboardWriter : ClipboardWriter {

    override fun write(text: String): Result<Unit> = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
