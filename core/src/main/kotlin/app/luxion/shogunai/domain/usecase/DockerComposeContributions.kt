package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.WorktreeError
import java.security.MessageDigest

const val COMPOSE_PROJECT_NAME = "COMPOSE_PROJECT_NAME"

private const val COMPOSE_PORT_KEY_PREFIX = "compose:"
private const val MAX_PROJECT_NAME_LENGTH = 50
private const val HASH_LENGTH = 6
private val UNSUPPORTED_CHARACTERS = Regex("[^a-z0-9_-]")

fun composeProjectName(repositoryFolder: String, worktreeFolder: String): String {
    val raw = "$repositoryFolder-$worktreeFolder"
    val normalized = normalizeComposeName(raw)
    val needsHash = worktreeFolder.isAmbiguousAfterNormalizing() || normalized.length > MAX_PROJECT_NAME_LENGTH
    if (!needsHash) return normalized
    val hash = shortHash("$repositoryFolder/$worktreeFolder")
    val prefix = normalized.take(MAX_PROJECT_NAME_LENGTH - HASH_LENGTH - 1).trimEnd('-', '_')
    return "$prefix-$hash"
}

private fun normalizeComposeName(raw: String): String =
    raw.lowercase().replace(UNSUPPORTED_CHARACTERS, "-").trimStart('-', '_')

private fun String.isAmbiguousAfterNormalizing(): Boolean {
    val hasUnsupported = UNSUPPORTED_CHARACTERS.containsMatchIn(lowercase())
    val mixedCase = any { it.isLowerCase() } && any { it.isUpperCase() }
    return hasUnsupported || mixedCase
}

private fun shortHash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)

fun dockerComposeContributions(settings: DockerComposeProvider): List<EnvironmentContribution> =
    listOf(composeProjectNameContribution) + settings.portVariables.map(::composePortContribution)

private val composeProjectNameContribution = EnvironmentContribution(
    variables = { emptyList() },
    names = setOf(COMPOSE_PROJECT_NAME),
    contextual = { context -> listOf(COMPOSE_PROJECT_NAME to composeProjectName(context.repositoryFolder, context.worktreeFolder)) },
)

private fun composePortContribution(name: String): EnvironmentContribution {
    val key = COMPOSE_PORT_KEY_PREFIX + name
    return EnvironmentContribution(
        ports = listOf(
            PortRequest(key, FIRST_VARIABLE_PORT..LAST_VARIABLE_PORT) {
                WorktreeError.NoFreePort(FIRST_VARIABLE_PORT, LAST_VARIABLE_PORT)
            },
        ),
        variables = { reservations -> listOf(name to reservations.ports.getValue(key).toString()) },
        names = setOf(name),
    )
}
