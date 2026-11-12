package app.luxion.shogunai.domain.model

internal fun parseLsofWorkingDirectories(output: String): List<Pair<String, String>> {
    val processes = mutableListOf<Pair<String, String>>()
    var pid: String? = null
    output.lineSequence().forEach { line ->
        when {
            line.startsWith("p") -> pid = line.drop(1)
            line.startsWith("n") -> pid?.let { processes += it to line.drop(1) }
        }
    }
    return processes
}
