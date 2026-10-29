package app.luxion.shogunai.domain.model

object EnvironmentCommand {

    fun posix(variables: List<Pair<String, String>>, command: String): String =
        if (variables.isEmpty()) command else "env " + variables.joinToString(" ") { (name, value) -> ShellQuoting.posix("$name=$value") } + " " + command

    fun powershell(variables: List<Pair<String, String>>, command: String): String =
        (powershellAssignments(variables) + command).joinToString("; ")

    fun powershellAssignments(variables: List<Pair<String, String>>): List<String> =
        variables.map { (name, value) -> "\$env:$name = ${ShellQuoting.powershell(value)}" }
}
