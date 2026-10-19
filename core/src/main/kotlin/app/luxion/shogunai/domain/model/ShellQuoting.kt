package app.luxion.shogunai.domain.model

object ShellQuoting {

    fun posix(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private val SAFE_WORD = Regex("[A-Za-z0-9_./:=@%+,-]+")

    fun posixCommand(command: List<String>): String =
        command.joinToString(" ") { if (SAFE_WORD.matches(it)) it else posix(it) }

    fun windows(value: String): String = "\"" + value + "\""
}
