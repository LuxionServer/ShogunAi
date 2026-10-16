package app.luxion.shogunai.domain.executor

interface ClipboardWriter {

    fun write(text: String): Result<Unit>
}
