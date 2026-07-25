package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.ClipboardWriter

class FakeClipboardWriter(
    private val responder: (text: String) -> Result<Unit> = { Result.success(Unit) },
) : ClipboardWriter {

    val writtenText = mutableListOf<String>()

    override fun write(text: String): Result<Unit> {
        writtenText += text
        return responder(text)
    }
}
