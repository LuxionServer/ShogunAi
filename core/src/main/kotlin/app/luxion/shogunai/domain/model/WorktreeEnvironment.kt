package app.luxion.shogunai.domain.model

data class EnvironmentDeclaration(
    val variables: List<EnvironmentVariable> = emptyList(),
    val envFile: String? = null,
) {
    val isEmpty: Boolean get() = variables.isEmpty()

    val portKeys: List<String>
        get() = variables.flatMap { variable -> variable.template.filterIsInstance<TemplatePart.Port>().map { it.key } }.distinct()
}

data class EnvironmentVariable(val name: String, val template: List<TemplatePart>, val source: String)

sealed interface TemplatePart {
    data class Literal(val text: String) : TemplatePart
    data object WorktreeName : TemplatePart
    data class Port(val key: String) : TemplatePart
    data class RepositoryPort(val alias: String, val key: String) : TemplatePart
}

data class WorktreeEnvironment(
    val variables: List<Pair<String, String>> = emptyList(),
    val ports: Map<String, Int> = emptyMap(),
    val resources: Map<String, String> = emptyMap(),
    val awaitingApproval: List<String> = emptyList(),
    val declarationApproved: Boolean = true,
    val unresolved: List<UnresolvedVariable> = emptyList(),
) {
    val isEmpty: Boolean get() = variables.isEmpty()
}

data class UnresolvedVariable(val name: String, val reason: UnresolvedReason)

sealed interface UnresolvedReason {
    data class RepositoryNotApproved(val alias: String, val repositoryPath: String) : UnresolvedReason
    data class PortNotDeclared(val alias: String, val key: String) : UnresolvedReason
    data class PortNotReserved(val alias: String, val key: String) : UnresolvedReason
    data class WorktreeUnreadable(val alias: String, val detail: String) : UnresolvedReason
    data class InvalidDeclaration(val declarationPath: String, val detail: String) : UnresolvedReason
}

class InvalidEnvironmentTemplate(message: String, val issue: RepositoryConfigIssue) : Exception(message)

private val VARIABLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val PORT_KEY = Regex("[A-Za-z0-9_-]+")
private val REPOSITORY_PORT = Regex("port:([a-z0-9-]+)\\.([A-Za-z0-9_-]+)")
private const val SHARED_PORT_PREFIX = "port:"

fun isValidVariableName(name: String): Boolean = VARIABLE_NAME.matches(name)

private val RESERVED_VARIABLE_NAMES = setOf(
    "PATH", "HOME", "SHELL", "IFS", "ENV", "BASH_ENV", "ZDOTDIR", "PROMPT_COMMAND",
    "NODE_OPTIONS", "NODE_PATH", "PYTHONPATH", "PYTHONSTARTUP", "PYTHONHOME", "RUBYOPT", "RUBYLIB",
    "PERL5OPT", "PERL5LIB", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH",
    "USER", "LOGNAME", "TMPDIR", "TMP", "TEMP", "PS1", "PS2", "PS4", "PROMPT", "RPROMPT",
    "SHELLOPTS", "BASHOPTS", "CDPATH", "FPATH", "HISTFILE", "INPUTRC",
    "PAGER", "MANPAGER", "EDITOR", "VISUAL", "BROWSER", "SUDO_ASKPASS",
    "SSL_CERT_FILE", "SSL_CERT_DIR", "CURL_CA_BUNDLE", "REQUESTS_CA_BUNDLE",
    "GOFLAGS", "GOPROXY", "GOPATH", "GOROOT", "GOBIN", "GOENV", "GOTOOLCHAIN", "GOSUMDB", "GONOSUMDB",
    "GOPRIVATE", "GONOPROXY", "GOINSECURE", "GOWORK", "GOCACHE", "GOMODCACHE",
    "RUSTFLAGS", "RUSTDOCFLAGS", "CC", "CXX", "CPP", "LD", "AR", "AS",
    "CFLAGS", "CXXFLAGS", "CPPFLAGS", "LDFLAGS", "MAKEFLAGS", "MAKE", "PKG_CONFIG_PATH", "MAVEN_ARGS",
    "KUBECONFIG", "AWS_CONFIG_FILE", "AWS_SHARED_CREDENTIALS_FILE", "CLOUDSDK_CONFIG", "AZURE_CONFIG_DIR",
    "DBUS_SESSION_BUS_ADDRESS", "OPENAI_BASE_URL",
)

private val RESERVED_VARIABLE_PREFIXES = listOf(
    "LD_", "DYLD_", "GIT_", "XDG_", "LESS", "SSH_", "NODE_", "NPM_CONFIG_", "YARN_", "PNPM_", "BUN_",
    "PIP_", "PYTHON", "UV_", "POETRY_", "CONDA", "GEM_", "BUNDLE_", "RUBY", "PERL", "CARGO_", "RUSTC",
    "RUSTUP_", "CMAKE_", "CCACHE_", "DOCKER_", "GH_", "ANTHROPIC_", "CLAUDE_", "CODEX_", "HERDR_", "ORCA_", "SHOGUNAI_",
)

private val RESERVED_VARIABLE_SUFFIXES = listOf("_PROXY", "_OPTS", "_HOME")

private val ALLOWED_VARIABLE_NAMES = setOf("NODE_ENV")

fun isReservedVariableName(name: String): Boolean {
    val upper = name.uppercase()
    if (upper in ALLOWED_VARIABLE_NAMES) return false
    return upper in RESERVED_VARIABLE_NAMES ||
        RESERVED_VARIABLE_PREFIXES.any(upper::startsWith) ||
        RESERVED_VARIABLE_SUFFIXES.any(upper::endsWith)
}

fun hasHiddenCharacters(text: String): Boolean =
    text.codePoints().anyMatch { codePoint ->
        when (Character.getType(codePoint).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
            else -> false
        }
    }

fun portKeyLabel(key: String): String = key.removePrefix(SHARED_PORT_PREFIX)

fun parseEnvironmentVariable(name: String, value: String): EnvironmentVariable =
    parseTemplate(name, value) { content -> placeholder(name, content) }

fun parseSharedEnvironmentVariable(name: String, value: String, aliases: Set<String>): EnvironmentVariable =
    parseTemplate(name, value) { content -> sharedPlaceholder(name, content, aliases) }

private fun parseTemplate(name: String, value: String, placeholder: (String) -> TemplatePart): EnvironmentVariable {
    if (!isValidVariableName(name)) throw InvalidEnvironmentTemplate("\"$name\" isn't a valid variable name", RepositoryConfigIssue.InvalidVariableName)
    if (isReservedVariableName(name)) {
        throw InvalidEnvironmentTemplate(
            "\"$name\" is a reserved variable name because it changes how other tools behave",
            RepositoryConfigIssue.ReservedVariableName,
        )
    }
    if (hasHiddenCharacters(value)) {
        throw InvalidEnvironmentTemplate(
            "\"$name\" can't contain line breaks, control characters or invisible format characters",
            RepositoryConfigIssue.InvalidVariableValue,
        )
    }
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
                if (end < 0) throw InvalidEnvironmentTemplate("unclosed placeholder in \"$name\"", RepositoryConfigIssue.UnclosedPlaceholder)
                if (literal.isNotEmpty()) parts += TemplatePart.Literal(literal.toString()).also { literal.clear() }
                parts += placeholder(value.substring(index + 2, end))
                index = end + 1
            }
            else -> {
                literal.append(value[index])
                index++
            }
        }
    }
    if (literal.isNotEmpty()) parts += TemplatePart.Literal(literal.toString())
    return EnvironmentVariable(name, parts, value)
}

private fun placeholder(name: String, content: String): TemplatePart = when {
    content == "worktree" -> TemplatePart.WorktreeName
    content == "port" -> TemplatePart.Port(name)
    content.startsWith("port:") && PORT_KEY.matches(content.removePrefix("port:")) ->
        TemplatePart.Port(SHARED_PORT_PREFIX + content.removePrefix("port:"))
    else -> throw InvalidEnvironmentTemplate(
        "unknown placeholder \${$content} in \"$name\"",
        RepositoryConfigIssue.UnknownPlaceholder(content),
    )
}

private fun sharedPlaceholder(name: String, content: String, aliases: Set<String>): TemplatePart {
    if (content == "worktree") return TemplatePart.WorktreeName
    REPOSITORY_PORT.matchEntire(content)?.let { match ->
        val (alias, key) = match.destructured
        if (alias !in aliases) {
            throw InvalidEnvironmentTemplate(
                "\${$content} in \"$name\" names '$alias', which isn't a repository of the multirepo",
                RepositoryConfigIssue.UnknownMultirepoRepository(alias),
            )
        }
        return TemplatePart.RepositoryPort(alias, SHARED_PORT_PREFIX + key)
    }
    if (content == "port" || content.startsWith(SHARED_PORT_PREFIX)) {
        throw InvalidEnvironmentTemplate(
            "\${$content} in \"$name\" isn't allowed: ports a repository uses itself are declared in its own .shogunai.json, " +
                "and a multirepo refers to them as \${port:<alias>.<key>}",
            RepositoryConfigIssue.OwnPortInSharedVariable,
        )
    }
    throw InvalidEnvironmentTemplate("unknown placeholder \${$content} in \"$name\"", RepositoryConfigIssue.UnknownPlaceholder(content))
}

val EnvironmentVariable.repositoryPorts: List<TemplatePart.RepositoryPort>
    get() = template.filterIsInstance<TemplatePart.RepositoryPort>()

fun EnvironmentVariable.render(
    worktreeName: String,
    ports: Map<String, Int>,
    repositoryPorts: Map<TemplatePart.RepositoryPort, Int> = emptyMap(),
): String =
    template.joinToString("") { part ->
        when (part) {
            is TemplatePart.Literal -> part.text
            TemplatePart.WorktreeName -> worktreeName
            is TemplatePart.Port -> ports.getValue(part.key).toString()
            is TemplatePart.RepositoryPort -> repositoryPorts.getValue(part).toString()
        }
    }
