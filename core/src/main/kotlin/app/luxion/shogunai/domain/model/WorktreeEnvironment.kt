package app.luxion.shogunai.domain.model

data class EnvironmentDeclaration(
    val variables: List<EnvironmentVariable> = emptyList(),
    val envFile: String? = null,
) {
    val isEmpty: Boolean get() = variables.isEmpty()

    val portKeys: List<String>
        get() = variables.flatMap { variable -> variable.template.filterIsInstance<TemplatePart.Port>().map { it.key } }.distinct()
}

data class EnvironmentVariable(val name: String, val template: List<TemplatePart>)

sealed interface TemplatePart {
    data class Literal(val text: String) : TemplatePart
    data object WorktreeName : TemplatePart
    data class Port(val key: String) : TemplatePart
}

data class WorktreeEnvironment(
    val variables: List<Pair<String, String>> = emptyList(),
    val ports: Map<String, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = variables.isEmpty()
}

class InvalidEnvironmentTemplate(message: String) : Exception(message)

private val VARIABLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val PORT_KEY = Regex("[A-Za-z0-9_-]+")
private const val SHARED_PORT_PREFIX = "port:"

fun isValidVariableName(name: String): Boolean = VARIABLE_NAME.matches(name)

fun portKeyLabel(key: String): String = key.removePrefix(SHARED_PORT_PREFIX)

fun parseEnvironmentVariable(name: String, value: String): EnvironmentVariable {
    if (!isValidVariableName(name)) throw InvalidEnvironmentTemplate("\"$name\" isn't a valid variable name")
    val parts = mutableListOf<TemplatePart>()
    val literal = StringBuilder()
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("$$", index) -> {
                literal.append('$')
                index += 2
            }
            value.startsWith("\${", index) -> {
                val end = value.indexOf('}', index)
                if (end < 0) throw InvalidEnvironmentTemplate("unclosed placeholder in \"$name\"")
                if (literal.isNotEmpty()) parts += TemplatePart.Literal(literal.toString()).also { literal.clear() }
                parts += placeholder(name, value.substring(index + 2, end))
                index = end + 1
            }
            else -> {
                literal.append(value[index])
                index++
            }
        }
    }
    if (literal.isNotEmpty()) parts += TemplatePart.Literal(literal.toString())
    return EnvironmentVariable(name, parts)
}

private fun placeholder(name: String, content: String): TemplatePart = when {
    content == "worktree" -> TemplatePart.WorktreeName
    content == "port" -> TemplatePart.Port(name)
    content.startsWith("port:") && PORT_KEY.matches(content.removePrefix("port:")) ->
        TemplatePart.Port(SHARED_PORT_PREFIX + content.removePrefix("port:"))
    else -> throw InvalidEnvironmentTemplate("unknown placeholder \${$content} in \"$name\"")
}

fun EnvironmentVariable.render(worktreeName: String, ports: Map<String, Int>): String =
    template.joinToString("") { part ->
        when (part) {
            is TemplatePart.Literal -> part.text
            TemplatePart.WorktreeName -> worktreeName
            is TemplatePart.Port -> ports.getValue(part.key).toString()
        }
    }
