package app.luxion.shogunai.domain.model

enum class MobileSecretKind(val id: String) {
    LOCAL_PROPERTIES("local-properties"),
    SIGNING_PROPERTIES("signing-properties"),
    KEYSTORE("keystore"),
    FIREBASE_CONFIG("firebase-config"),
    ;

    companion object {
        private val FIREBASE_CONFIG_FILES = setOf("google-services.json", "GoogleService-Info.plist", "firebase_options.dart")

        fun of(fileName: String): MobileSecretKind? = when {
            fileName == "local.properties" -> LOCAL_PROPERTIES
            fileName == "keystore.properties" || fileName == "key.properties" -> SIGNING_PROPERTIES
            fileName.endsWith(".jks") || fileName.endsWith(".keystore") -> KEYSTORE
            fileName in FIREBASE_CONFIG_FILES -> FIREBASE_CONFIG
            else -> null
        }
    }
}

val GENERATED_FOLDERS = setOf("build", ".gradle", ".dart_tool", "node_modules", "Pods", "DerivedData")

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
