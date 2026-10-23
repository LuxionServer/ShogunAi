package app.luxion.shogunai.infrastructure

internal object WindowsArguments {
    private val BATCH_EXTENSIONS = listOf(".cmd", ".bat")

    fun prepare(command: List<String>, osName: String): List<String> {
        if (!osName.startsWith("Windows") || command.size < 2 || isBatchFile(command.first())) return command
        return listOf(command.first()) + command.drop(1).map(::quote)
    }

    fun quote(argument: String): String {
        if (argument.isNotEmpty() && argument.none { it == ' ' || it == '\t' || it == '"' }) return argument
        val quoted = StringBuilder("\"")
        var backslashes = 0
        for (char in argument) {
            when (char) {
                '\\' -> backslashes++
                '"' -> {
                    quoted.append("\\".repeat(backslashes * 2 + 1)).append(char)
                    backslashes = 0
                }
                else -> {
                    quoted.append("\\".repeat(backslashes)).append(char)
                    backslashes = 0
                }
            }
        }
        return quoted.append("\\".repeat(backslashes * 2)).append('"').toString()
    }

    private fun isBatchFile(executable: String): Boolean =
        BATCH_EXTENSIONS.any { executable.endsWith(it, ignoreCase = true) }
}
