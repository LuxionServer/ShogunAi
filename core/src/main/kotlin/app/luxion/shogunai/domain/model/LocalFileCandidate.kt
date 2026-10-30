package app.luxion.shogunai.domain.model

data class LocalFileCandidate(
    val path: String,
    val isFolder: Boolean,
    val secretKind: MobileSecretKind? = null,
    val providedByRepository: Boolean = false,
)
