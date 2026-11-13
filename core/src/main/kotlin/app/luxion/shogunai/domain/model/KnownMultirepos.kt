package app.luxion.shogunai.domain.model

data class KnownMultirepos(
    val available: List<Multirepo> = emptyList(),
    val unavailable: List<UnavailableMultirepo> = emptyList(),
)

data class UnavailableMultirepo(val folder: String, val reason: MultirepoUnavailability) {
    val name: String get() = folder.substringAfterLast('/').ifEmpty { folder }
    val declarationPath: String get() = joinPath(folder, MULTIREPO_FILE_NAME)
}

sealed interface MultirepoUnavailability {
    data object FolderUnreachable : MultirepoUnavailability
    data object DeclarationMissing : MultirepoUnavailability
    data class InvalidDeclaration(val detail: String) : MultirepoUnavailability
}
