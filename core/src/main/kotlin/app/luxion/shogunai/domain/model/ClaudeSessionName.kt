package app.luxion.shogunai.domain.model

const val SESSION_NAME_MAX_LENGTH = 64

fun sessionName(vararg parts: String): String =
    parts.map(::sanitizedPart).filter { it.isNotEmpty() }.joinToString("-").take(SESSION_NAME_MAX_LENGTH).trimEnd('-')

fun groupMemberSessionName(alias: String, groupTitle: String): String = sessionName(alias, groupTitle)

fun worktreeSessionName(projectName: String, worktree: Worktree): String =
    if (worktree.isMain) sessionName(projectName) else sessionName(projectName, folderName(worktree.path))

fun multirepoSessionName(multirepo: Multirepo): String = sessionName(multirepo.name)

fun namedClaudeCommand(command: String, name: String): String? {
    if (name.isEmpty() || command.any { it in CONTROL_CHARACTERS }) return null
    val tokens = command.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.any { it == "-n" || it == "--name" || it.startsWith("--name=") }) return null
    var index = 0
    while (index < tokens.size && (tokens[index] == "env" || ASSIGNMENT.matches(tokens[index]))) index++
    if (tokens.getOrNull(index)?.let(::baseName) == HEADROOM && tokens.getOrNull(index + 1) == "wrap") index += 2
    if (tokens.getOrNull(index)?.let(::baseName) != CLAUDE) return null
    return "${command.trim()} -n $name"
}

fun uniqueSessionName(name: String, taken: Set<String>): String =
    generateSequence(1) { it + 1 }
        .map { suffix -> if (suffix == 1) name else "$name-$suffix" }
        .first { it !in taken }

private fun sanitizedPart(part: String): String = part.lowercase().replace(OUTSIDE_SET, "-").trim('-')

private fun folderName(path: String): String = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

private fun baseName(token: String): String = token.substringAfterLast('/')

private const val CLAUDE = "claude"
private const val HEADROOM = "headroom"
private val CONTROL_CHARACTERS = setOf(';', '&', '|', '\n', '\r', '`', '(', ')')
private val WHITESPACE = Regex("\\s+")
private val ASSIGNMENT = Regex("[A-Za-z_][A-Za-z0-9_]*=\\S*")
private val OUTSIDE_SET = Regex("[^a-z0-9]+")
