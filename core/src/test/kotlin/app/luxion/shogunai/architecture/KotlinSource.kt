package app.luxion.shogunai.architecture

const val PROJECT_PACKAGE = "app.luxion.shogunai"

data class SourceReference(val target: String, val line: Int)

class KotlinSource(val path: String, val module: String, val sourceSet: String, val text: String) {

    val scanned: ScannedSource by lazy { KotlinSourceScanner.scan(text) }

    val fileName: String get() = path.substringAfterLast('/')

    val isProduction: Boolean get() = sourceSet == "main"

    val lineCount: Int
        get() = text.split('\n').let { if (it.last().isEmpty()) it.size - 1 else it.size }

    val packageName: String by lazy {
        scanned.code.firstNotNullOfOrNull { PACKAGE.find(it)?.groupValues?.get(1) }.orEmpty()
    }

    val imports: List<SourceReference> by lazy {
        scanned.code.withIndex().mapNotNull { (index, code) ->
            IMPORT.find(code)?.let { SourceReference(it.groupValues[1].replace("`", ""), index + 1) }
        }
    }

    val projectReferences: List<SourceReference> by lazy {
        val qualified = scanned.code.withIndex()
            .filterNot { (_, code) -> PACKAGE.containsMatchIn(code) || IMPORT.containsMatchIn(code) }
            .flatMap { (index, code) -> QUALIFIED.findAll(code).map { SourceReference(it.value, index + 1) } }
        imports.filter { it.target.startsWith("$PROJECT_PACKAGE.") } + qualified
    }

    fun location(line: Int): String = "$path:$line"

    private companion object {
        val PACKAGE = Regex("""^\s*package\s+([\w.]+)""")
        val IMPORT = Regex("""^\s*import\s+([\w`]+(?:\.[\w`]+)*(?:\.\*)?)""")
        val QUALIFIED = Regex("""\b${Regex.escape(PROJECT_PACKAGE)}(?:\.\w+)+""")
    }
}
