package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.Stack
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.suggestProviders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectRepositoryStacksUseCaseTest {

    private val root = "/projects/main-repo"

    private fun detect(
        files: List<String>,
        directories: Set<String> = emptySet(),
        texts: Map<String, String> = emptyMap(),
        unreadable: Set<String> = emptySet(),
    ): List<DetectedStack> {
        val fileManager = FakeFileManager(existing = files.map { "$root/$it" }, directories = directories.map { "$root/$it" }.toSet())
        texts.forEach { (path, text) -> fileManager.writeText("$root/$path", text) }
        unreadable.forEach { path -> fileManager.unreadableTexts += "$root/$path" }
        return DetectRepositoryStacksUseCase(fileManager)(root)
    }

    @Test
    fun `detects a gradle repository from its wrapper and settings`() {
        val stacks = detect(listOf("gradlew", "settings.gradle.kts"))

        assertEquals(listOf(DetectedStack(Stack.GRADLE, listOf("gradlew", "settings.gradle.kts"))), stacks)
    }

    @Test
    fun `detects android from a plugin applied in a module`() {
        val stacks = detect(
            files = listOf("gradlew", "app/build.gradle.kts"),
            directories = setOf("app"),
            texts = mapOf("app/build.gradle.kts" to "plugins { id(\"com.android.application\") }"),
        )

        assertEquals(DetectedStack(Stack.ANDROID, listOf("app/build.gradle.kts")), stacks.single { it.stack == Stack.ANDROID })
    }

    @Test
    fun `detects android from the version catalog`() {
        val stacks = detect(
            files = listOf("gradle/libs.versions.toml"),
            directories = setOf("gradle"),
            texts = mapOf("gradle/libs.versions.toml" to "agp = { id = \"com.android.library\" }"),
        )

        assertEquals(listOf("gradle/libs.versions.toml"), stacks.single { it.stack == Stack.ANDROID }.evidence)
    }

    @Test
    fun `a plain gradle module is not android`() {
        val stacks = detect(
            files = listOf("app/build.gradle.kts"),
            directories = setOf("app"),
            texts = mapOf("app/build.gradle.kts" to "plugins { kotlin(\"jvm\") }"),
        )

        assertTrue(stacks.none { it.stack == Stack.ANDROID })
    }

    @Test
    fun `detects a gradle build in a subfolder when the main folder has none`() {
        val stacks = detect(files = listOf("android/settings.gradle.kts", "android/build.gradle.kts"), directories = setOf("android"))

        assertEquals(DetectedStack(Stack.GRADLE, listOf("android/settings.gradle.kts")), stacks.single { it.stack == Stack.GRADLE })
    }

    @Test
    fun `a nested build next to a root build leaves only the main folder's files as evidence`() {
        val stacks = detect(files = listOf("settings.gradle.kts", "build-logic/settings.gradle.kts"), directories = setOf("build-logic"))

        assertEquals(listOf(DetectedStack(Stack.GRADLE, listOf("settings.gradle.kts"))), stacks)
    }

    @Test
    fun `detects android declared in a settings file`() {
        val stacks = detect(
            files = listOf("android/settings.gradle.kts"),
            directories = setOf("android"),
            texts = mapOf("android/settings.gradle.kts" to "plugins { id(\"com.android.application\") version \"8.7.0\" apply false }"),
        )

        assertEquals(DetectedStack(Stack.ANDROID, listOf("android/settings.gradle.kts")), stacks.single { it.stack == Stack.ANDROID })
    }

    @Test
    fun `detects a flutter app with its android and ios folders`() {
        val stacks = detect(
            files = listOf(
                "pubspec.yaml",
                "android/settings.gradle.kts",
                "android/build.gradle.kts",
                "android/app/build.gradle.kts",
                "ios/Runner.xcodeproj",
            ),
            directories = setOf("android", "android/app", "ios"),
            texts = mapOf(
                "pubspec.yaml" to "name: sample_app\ndependencies:\n  flutter:\n    sdk: flutter\n",
                "android/settings.gradle.kts" to "plugins {\n    id(\"com.android.application\") version \"8.7.0\" apply false\n}",
                "android/build.gradle.kts" to "allprojects { repositories { google() } }",
                "android/app/build.gradle.kts" to "plugins { id(\"com.android.application\") }",
            ),
        )

        assertEquals(
            listOf(
                DetectedStack(Stack.FLUTTER, listOf("pubspec.yaml")),
                DetectedStack(Stack.GRADLE, listOf("android/settings.gradle.kts")),
                DetectedStack(Stack.ANDROID, listOf("android/settings.gradle.kts")),
                DetectedStack(Stack.XCODE, listOf("ios/Runner.xcodeproj")),
            ),
            stacks,
        )
    }

    @Test
    fun `a dart package is not flutter`() {
        val stacks = detect(files = listOf("pubspec.yaml"), texts = mapOf("pubspec.yaml" to "name: sample_package\ndependencies:\n  path: ^1.9.0\n"))

        assertTrue(stacks.isEmpty())
    }

    @Test
    fun `detects an xcode project in a subfolder`() {
        val stacks = detect(files = listOf("iosApp/iosApp.xcodeproj"), directories = setOf("iosApp"))

        assertEquals(DetectedStack(Stack.XCODE, listOf("iosApp/iosApp.xcodeproj")), stacks.single())
    }

    @Test
    fun `detects a swift package in the main folder`() {
        val stacks = detect(listOf("Package.swift"))

        assertEquals(DetectedStack(Stack.XCODE, listOf("Package.swift")), stacks.single())
    }

    @Test
    fun `detects maven from pom or wrapper`() {
        assertEquals(DetectedStack(Stack.MAVEN, listOf("mvnw", "pom.xml")), detect(listOf("pom.xml", "mvnw")).single())
    }

    @Test
    fun `detects every compose file name`() {
        listOf("compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml").forEach { name ->
            assertEquals(DetectedStack(Stack.DOCKER_COMPOSE, listOf(name)), detect(listOf(name)).single())
        }
    }

    @Test
    fun `detects nothing in a repository without known files`() {
        assertTrue(detect(listOf("README.md", "src/Main.kt"), directories = setOf("src")).isEmpty())
    }

    @Test
    fun `skips hidden and build folders`() {
        val stacks = detect(
            files = listOf("build/pom.xml", ".cache/Package.swift", "node_modules/app.xcodeproj"),
            directories = setOf("build", ".cache", "node_modules"),
        )

        assertTrue(stacks.isEmpty())
    }

    @Test
    fun `an unreadable build file only loses the stacks that depend on its text`() {
        val stacks = detect(
            files = listOf("pom.xml", "app/build.gradle.kts"),
            directories = setOf("app"),
            unreadable = setOf("app/build.gradle.kts"),
        )

        assertEquals(listOf(Stack.MAVEN), stacks.map { it.stack })
    }

    @Test
    fun `an unreadable repository detects nothing`() {
        val fileManager = FakeFileManager()
        fileManager.unreadable += root

        assertTrue(DetectRepositoryStacksUseCase(fileManager)(root).isEmpty())
    }

    @Test
    fun `suggests gradle and emulator for an android gradle repository`() {
        val stacks = listOf(
            DetectedStack(Stack.GRADLE, listOf("gradlew")),
            DetectedStack(Stack.ANDROID, listOf("app/build.gradle.kts")),
        )

        val report = suggestProviders(stacks, RepositoryProviders(), isMac = true)

        assertEquals(listOf(SuggestedProvider.GRADLE, SuggestedProvider.EMULATOR), report.suggestions.map { it.provider })
        assertEquals(listOf("app/build.gradle.kts"), report.suggestions.last().evidence)
    }

    @Test
    fun `suggests every provider of a flutter repository on a mac`() {
        val stacks = listOf(
            DetectedStack(Stack.FLUTTER, listOf("pubspec.yaml")),
            DetectedStack(Stack.GRADLE, listOf("android/settings.gradle.kts")),
            DetectedStack(Stack.ANDROID, listOf("android/settings.gradle.kts")),
            DetectedStack(Stack.XCODE, listOf("ios/Runner.xcodeproj")),
        )

        val report = suggestProviders(stacks, RepositoryProviders(), isMac = true)

        assertEquals(
            listOf(
                SuggestedProvider.FLUTTER,
                SuggestedProvider.GRADLE,
                SuggestedProvider.EMULATOR,
                SuggestedProvider.IOS_SIMULATOR,
                SuggestedProvider.XCODE,
            ),
            report.suggestions.map { it.provider },
        )
        assertEquals(listOf("pubspec.yaml"), report.suggestions.first().evidence)
        assertTrue(suggestProviders(stacks.take(1), RepositoryProviders(flutter = FlutterProvider), isMac = true).suggestions.isEmpty())
    }

    @Test
    fun `does not suggest what the repository already declares`() {
        val stacks = listOf(DetectedStack(Stack.GRADLE, listOf("gradlew")), DetectedStack(Stack.MAVEN, listOf("pom.xml")))

        val report = suggestProviders(stacks, RepositoryProviders(gradle = GradleProvider(), maven = MavenProvider()), isMac = true)

        assertTrue(report.suggestions.isEmpty())
        assertEquals(listOf(SuggestedProvider.GRADLE, SuggestedProvider.MAVEN), report.declared.map { it.provider })
    }

    @Test
    fun `an xcode stack suggests the simulator and xcode providers only on a mac`() {
        val stacks = listOf(DetectedStack(Stack.XCODE, listOf("iosApp/iosApp.xcodeproj")))

        assertEquals(
            listOf(SuggestedProvider.IOS_SIMULATOR, SuggestedProvider.XCODE),
            suggestProviders(stacks, RepositoryProviders(), isMac = true).suggestions.map { it.provider },
        )
        assertTrue(suggestProviders(stacks, RepositoryProviders(), isMac = false).suggestions.isEmpty())
    }
}
