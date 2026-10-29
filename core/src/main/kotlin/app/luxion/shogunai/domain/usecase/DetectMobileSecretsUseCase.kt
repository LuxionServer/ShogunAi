package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.KeystoreReferenceWarning
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class DetectMobileSecretsUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
) {
    suspend operator fun invoke(config: ProjectConfig): MobileSecretsReport {
        val found = untrackedSecrets(config.baseRepositoryPath)
        val references = found.filter { it.kind in PROPERTIES_KINDS }.flatMap { keystoreReferences(config.baseRepositoryPath, it.path) }
        val referenced = references.mapNotNull { it.keystore }.map { MobileSecret(it, MobileSecretKind.KEYSTORE) }
        val provided = providedFiles(config) + found.filter { isWrittenByFlutter(config.baseRepositoryPath, it) }.map { it.path }
        return MobileSecretsReport(
            secrets = (found + referenced).distinctBy { it.path }.filterNot { it.path in provided },
            warnings = references.mapNotNull { it.warning },
        )
    }

    private suspend fun untrackedSecrets(repository: String): List<MobileSecret> {
        val command = listOf("git", "ls-files", "--others", "--ignored", "-z") + SECRET_PATTERNS.flatMap { listOf("-x", it) }
        val result = executor.execute(command = command, workingDirectory = repository)
        if (!result.isSuccess) throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        return result.stdout.split('\u0000')
            .filter { it.isNotEmpty() && it.split('/').dropLast(1).none { folder -> folder in EXCLUDED_FOLDERS } }
            .mapNotNull { path -> kindOf(path.substringAfterLast('/'))?.let { MobileSecret(path, it) } }
    }

    private fun isWrittenByFlutter(repository: String, secret: MobileSecret): Boolean =
        secret.kind == MobileSecretKind.LOCAL_PROPERTIES &&
            FLUTTER_SDK_PROPERTY in fileManager.readProperties("$repository/${secret.path}")

    private suspend fun keystoreReferences(repository: String, propertiesFile: String): List<KeystoreReference> =
        fileManager.readProperties("$repository/$propertiesFile")
            .filterKeys { key -> key.lowercase().filterNot { it in KEY_SEPARATORS }.endsWith(STORE_FILE_SUFFIX) }
            .toSortedMap()
            .mapNotNull { (key, rawValue) -> reference(repository, propertiesFile, key, rawValue.trim()) }

    private suspend fun reference(repository: String, propertiesFile: String, key: String, value: String): KeystoreReference? {
        val path = value.replace('\\', '/')
        if (path.isEmpty() || isAbsolute(path)) return null
        val folder = propertiesFile.substringBeforeLast('/', missingDelimiterValue = "")
        val candidates = listOf(joinRelative(folder, path), joinRelative("", path)).distinct()
        val inside = candidates.filterNotNull()
        if (inside.isEmpty()) return KeystoreReference(warning = KeystoreReferenceWarning(propertiesFile, key, value))
        val keystore = inside.firstOrNull { fileManager.exists("$repository/$it") && !isTracked(repository, it) }
        return keystore?.let { KeystoreReference(keystore = it) }
    }

    private suspend fun isTracked(repository: String, path: String): Boolean {
        val result = executor.execute(command = listOf("git", "ls-files", "-z", "--", path), workingDirectory = repository)
        return !result.isSuccess || result.stdout.isNotEmpty()
    }

    private suspend fun providedFiles(config: ProjectConfig): Set<String> {
        val provisioned = runCatching { resolveProvisionedFiles(config, executor, fileManager, provisioningSource) }
            .getOrNull() ?: return emptySet()
        val underLinkedFolders = provisioned.linkedFolders.flatMap { (folder, files) -> files.map { "$folder/$it" } }
        return (provisioned.copied + provisioned.linked + underLinkedFolders).toSet()
    }

    private data class KeystoreReference(val keystore: String? = null, val warning: KeystoreReferenceWarning? = null)

    private companion object {
        private val SECRET_PATTERNS = listOf(
            "local.properties",
            "keystore.properties",
            "key.properties",
            "*.jks",
            "*.keystore",
            "google-services.json",
            "GoogleService-Info.plist",
            "firebase_options.dart",
        )
        val FIREBASE_CONFIG_FILES = setOf("google-services.json", "GoogleService-Info.plist", "firebase_options.dart")
        private val PROPERTIES_KINDS = setOf(MobileSecretKind.LOCAL_PROPERTIES, MobileSecretKind.SIGNING_PROPERTIES)
        private val EXCLUDED_FOLDERS = setOf("build", ".gradle", ".dart_tool", "node_modules", "Pods", "DerivedData")
        private val KEY_SEPARATORS = setOf('.', '_', '-')
        const val STORE_FILE_SUFFIX = "storefile"
        const val FLUTTER_SDK_PROPERTY = "flutter.sdk"
        val WINDOWS_DRIVE = Regex("^[A-Za-z]:/")

        fun kindOf(name: String): MobileSecretKind? = when {
            name == "local.properties" -> MobileSecretKind.LOCAL_PROPERTIES
            name == "keystore.properties" || name == "key.properties" -> MobileSecretKind.SIGNING_PROPERTIES
            name.endsWith(".jks") || name.endsWith(".keystore") -> MobileSecretKind.KEYSTORE
            name in FIREBASE_CONFIG_FILES -> MobileSecretKind.FIREBASE_CONFIG
            else -> null
        }

        fun isAbsolute(path: String): Boolean = path.startsWith("/") || path.startsWith("~") || WINDOWS_DRIVE.containsMatchIn(path)

        fun joinRelative(folder: String, path: String): String? {
            val segments = mutableListOf<String>()
            (folder.split('/') + path.split('/')).filter { it.isNotEmpty() && it != "." }.forEach { segment ->
                if (segment == "..") {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.lastIndex)
                } else {
                    segments += segment
                }
            }
            return segments.joinToString("/").takeIf { it.isNotEmpty() }
        }
    }
}
