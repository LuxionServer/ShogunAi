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
}

data class WorktreeEnvironment(
    val variables: List<Pair<String, String>> = emptyList(),
    val ports: Map<String, Int> = emptyMap(),
    val resources: Map<String, String> = emptyMap(),
    val awaitingApproval: List<String> = emptyList(),
    val declarationApproved: Boolean = true,
) {
    val isEmpty: Boolean get() = variables.isEmpty()
}

class InvalidEnvironmentTemplate(message: String, val issue: RepositoryConfigIssue) : Exception(message)

private val VARIABLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val PORT_KEY = Regex("[A-Za-z0-9_-]+")
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
    "RUSTUP_", "CMAKE_", "CCACHE_", "DOCKER_", "GH_", "ANTHROPIC_", "CLAUDE_", "CODEX_", "HERDR_", "SHOGUNAI_",
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

fun parseEnvironmentVariable(name: String, value: String): EnvironmentVariable {
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

fun EnvironmentVariable.render(worktreeName: String, ports: Map<String, Int>): String =
    template.joinToString("") { part ->
        when (part) {
            is TemplatePart.Literal -> part.text
            TemplatePart.WorktreeName -> worktreeName
            is TemplatePart.Port -> ports.getValue(part.key).toString()
        }
    }
