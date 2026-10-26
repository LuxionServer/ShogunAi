package app.luxion.shogunai.domain.model

enum class MobileSecretKind(val id: String) {
    LOCAL_PROPERTIES("local-properties"),
    SIGNING_PROPERTIES("signing-properties"),
    KEYSTORE("keystore"),
    FIREBASE_CONFIG("firebase-config"),
}

data class MobileSecret(val path: String, val kind: MobileSecretKind) {
    val linkPattern: String get() = "/$path"
}

data class KeystoreReferenceWarning(val file: String, val key: String, val value: String)

data class MobileSecretsReport(
    val secrets: List<MobileSecret> = emptyList(),
    val warnings: List<KeystoreReferenceWarning> = emptyList(),
) {
    val isEmpty: Boolean get() = secrets.isEmpty() && warnings.isEmpty()
}
