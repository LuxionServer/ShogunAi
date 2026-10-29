package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.KeystoreReferenceWarning
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectMobileSecretsUseCaseTest {

    private val base = "/home/dev/projects/main-repo"
    private val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = emptyList())

    private fun executor(untracked: List<String>, tracked: Set<String> = emptySet(), provisioned: List<String> = emptyList()) =
        FakeShellCommandExecutor { command ->
            when {
                "local.properties" in command && "*.jks" in command -> success(command, stdout = untracked.joinToString("") { "$it\u0000" })
                command.getOrNull(2) == "-z" && command.getOrNull(3) == "--" ->
                    success(command, stdout = if (command.last() in tracked) "${command.last()}\u0000" else "")
                else -> success(command, stdout = provisioned.joinToString("") { "$it\u0000" })
            }
        }

    private suspend fun detect(
        untracked: List<String>,
        fileManager: FakeFileManager = FakeFileManager(),
        projectConfig: ProjectConfig = config,
        provisioning: RepositoryProvisioning = RepositoryProvisioning(),
        tracked: Set<String> = emptySet(),
        provisioned: List<String> = emptyList(),
    ): MobileSecretsReport = DetectMobileSecretsUseCase(
        executor(untracked, tracked, provisioned),
        fileManager,
        FakeRepositoryProvisioningSource(byRepository = mapOf(base to provisioning)),
    )(projectConfig)

    @Test
    fun `finds the untracked secrets of an Android repository with their kinds`() = runTest {
        val report = detect(listOf("local.properties", "app/google-services.json", "keys/release.jks", "app/src/debug/google-services.json"))

        assertEquals(
            listOf(
                MobileSecret("local.properties", MobileSecretKind.LOCAL_PROPERTIES),
                MobileSecret("app/google-services.json", MobileSecretKind.FIREBASE_CONFIG),
                MobileSecret("keys/release.jks", MobileSecretKind.KEYSTORE),
                MobileSecret("app/src/debug/google-services.json", MobileSecretKind.FIREBASE_CONFIG),
            ),
            report.secrets,
        )
        assertEquals("/app/google-services.json", report.secrets[1].linkPattern)
    }

    @Test
    fun `files that aren't secrets are left out`() = runTest {
        assertEquals(listOf(MobileSecret("local.properties", MobileSecretKind.LOCAL_PROPERTIES)), detect(listOf("notes.txt", "local.properties")).secrets)
    }

    @Test
    fun `asks git for untracked files only, by name`() = runTest {
        val executor = executor(emptyList())

        DetectMobileSecretsUseCase(executor, FakeFileManager(), FakeRepositoryProvisioningSource())(config)

        assertEquals(
            listOf(
                "git", "ls-files", "--others", "--ignored", "-z",
                "-x", "local.properties", "-x", "keystore.properties", "-x", "key.properties",
                "-x", "*.jks", "-x", "*.keystore", "-x", "google-services.json", "-x", "GoogleService-Info.plist",
                "-x", "firebase_options.dart",
            ),
            executor.executedCommands.first(),
        )
    }

    @Test
    fun `skips build output and tool folders`() = runTest {
        val report = detect(listOf("app/build/intermediates/local.properties", "ios/Pods/GoogleService-Info.plist", ".gradle/key.properties"))

        assertEquals(MobileSecretsReport(), report)
    }

    @Test
    fun `doesn't suggest what the provisioning already provides`() = runTest {
        val keys = "$base/keys"
        val fileManager = FakeFileManager(existing = listOf(keys), directories = setOf(keys)).also {
            it.directoryFiles[keys] = listOf("release.jks")
        }

        val report = detect(
            untracked = listOf("local.properties", "keys/release.jks", "app/google-services.json"),
            fileManager = fileManager,
            projectConfig = config.copy(secretFiles = listOf("keys")),
            provisioning = RepositoryProvisioning(copyPatterns = listOf("local.properties")),
            provisioned = listOf("local.properties"),
        )

        assertEquals(listOf(MobileSecret("app/google-services.json", MobileSecretKind.FIREBASE_CONFIG)), report.secrets)
    }

    @Test
    fun `warns about a relative keystore path that leaves the repository`() = runTest {
        val fileManager = FakeFileManager(properties = mapOf("$base/keystore.properties" to mapOf("storeFile" to "../signing/release.jks", "storePassword" to "placeholder")))

        val report = detect(listOf("keystore.properties"), fileManager)

        assertEquals(listOf(KeystoreReferenceWarning("keystore.properties", "storeFile", "../signing/release.jks")), report.warnings)
    }

    @Test
    fun `suggests a referenced keystore inside the repository whatever its extension`() = runTest {
        val fileManager = FakeFileManager(
            existing = listOf("$base/signing/upload.ks"),
            properties = mapOf("$base/local.properties" to mapOf("RELEASE_STORE_FILE" to "signing/upload.ks")),
        )

        val report = detect(listOf("local.properties"), fileManager)

        assertEquals(
            listOf(
                MobileSecret("local.properties", MobileSecretKind.LOCAL_PROPERTIES),
                MobileSecret("signing/upload.ks", MobileSecretKind.KEYSTORE),
            ),
            report.secrets,
        )
        assertEquals(emptyList(), report.warnings)
    }

    @Test
    fun `resolves a reference from the properties file's folder`() = runTest {
        val fileManager = FakeFileManager(
            existing = listOf("$base/android/upload.jks"),
            properties = mapOf("$base/android/app/key.properties" to mapOf("storeFile" to "../upload.jks")),
        )

        val report = detect(listOf("android/app/key.properties", "android/upload.jks"), fileManager)

        assertEquals(listOf("android/app/key.properties", "android/upload.jks"), report.secrets.map { it.path })
        assertEquals(emptyList(), report.warnings)
    }

    @Test
    fun `ignores absolute, missing and tracked keystore references`() = runTest {
        val fileManager = FakeFileManager(
            existing = listOf("$base/signing/tracked.jks"),
            properties = mapOf(
                "$base/key.properties" to mapOf(
                    "storeFile" to "/opt/keys/release.jks",
                    "debug.storeFile" to "signing/missing.jks",
                    "release.store-file" to "signing/tracked.jks",
                ),
            ),
        )

        val report = detect(listOf("key.properties"), fileManager, tracked = setOf("signing/tracked.jks"))

        assertEquals(listOf(MobileSecret("key.properties", MobileSecretKind.SIGNING_PROPERTIES)), report.secrets)
        assertEquals(emptyList(), report.warnings)
    }

    @Test
    fun `finds FlutterFire's options as Firebase configuration`() = runTest {
        val report = detect(listOf("lib/firebase_options.dart", ".dart_tool/firebase_options.dart"))

        assertEquals(listOf(MobileSecret("lib/firebase_options.dart", MobileSecretKind.FIREBASE_CONFIG)), report.secrets)
    }

    @Test
    fun `leaves out the local properties Flutter writes but still reads its keystore keys`() = runTest {
        val fileManager = FakeFileManager(
            existing = listOf("$base/android/upload.jks"),
            properties = mapOf(
                "$base/android/local.properties" to mapOf(
                    "flutter.sdk" to "/opt/flutter",
                    "sdk.dir" to "/opt/android-sdk",
                    "storeFile" to "upload.jks",
                ),
            ),
        )

        val report = detect(listOf("android/local.properties"), fileManager)

        assertEquals(listOf(MobileSecret("android/upload.jks", MobileSecretKind.KEYSTORE)), report.secrets)
    }
}
