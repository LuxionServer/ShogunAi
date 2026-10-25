package app.luxion.shogunai.domain.model

data class RepositoryApprovalSubject(
    val commands: List<List<String>> = emptyList(),
    val variables: List<Pair<String, String>> = emptyList(),
    val envFile: String? = null,
) {
    val isEmpty: Boolean get() = commands.isEmpty() && variables.isEmpty() && envFile == null

    val declaresEnvironment: Boolean get() = variables.isNotEmpty() || envFile != null

    val variableNames: List<String> get() = variables.map { it.first }
}

val RepositoryProvisioning.approvalSubject: RepositoryApprovalSubject
    get() = RepositoryApprovalSubject(
        commands = postCreateCommands,
        variables = environment.variables.map { it.name to it.source },
        envFile = environment.envFile,
    )
