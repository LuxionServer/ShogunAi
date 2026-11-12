package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleJavaSettingsReaderTest {

    private val repository = "/home/dev/projects/main-repo"
    private val home = "/home/dev"
    private val userProperties = "$home/.gradle/gradle.properties"
    private val repositoryProperties = "$repository/gradle.properties"

    private fun reader(
        texts: Map<String, String> = emptyMap(),
        properties: Map<String, Map<String, String>> = emptyMap(),
        files: List<String> = emptyList(),
        variables: Map<String, String> = emptyMap(),
    ): GradleJavaSettingsReader {
        val fileManager = FakeFileManager(existing = files, properties = properties)
        texts.forEach { (path, text) -> fileManager.writeText(path, text) }
        return GradleJavaSettingsReader(fileManager, { variables[it] }, home)
    }

    private fun read(
        texts: Map<String, String> = emptyMap(),
        properties: Map<String, Map<String, String>> = emptyMap(),
        files: List<String> = emptyList(),
    ): GradleJavaSettings = reader(texts, properties, files).read(repository)

    @Test
    fun `a repository without Java settings reads the defaults`() {
        assertEquals(GradleJavaSettings(), read())
    }

    @Test
    fun `Gradle's user home is GRADLE_USER_HOME when set, else the home's gradle folder`() {
        assertEquals("$home/.gradle", reader().gradleUserHome)
        assertEquals("$home/.gradle", reader(variables = mapOf("GRADLE_USER_HOME" to " ")).gradleUserHome)
        assertEquals("/opt/gradle-home", reader(variables = mapOf("GRADLE_USER_HOME" to "/opt/gradle-home")).gradleUserHome)
    }

    @Test
    fun `org gradle java home from the user's properties wins over the repository's`() {
        val settings = read(
            properties = mapOf(
                userProperties to mapOf("org.gradle.java.home" to "/opt/jdk-21"),
                repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17"),
            ),
        )

        assertEquals(GradleJavaHome("/opt/jdk-21", userProperties), settings.javaHome)
    }

    @Test
    fun `org gradle java home from the repository's properties names that file`() {
        val settings = read(properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17")))

        assertEquals(GradleJavaHome("/opt/jdk-17", repositoryProperties), settings.javaHome)
    }

    @Test
    fun `a blank org gradle java home is ignored`() {
        assertNull(read(properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to " "))).javaHome)
    }

    @Test
    fun `installation paths and the auto-detect and auto-download switches are read`() {
        val settings = read(
            properties = mapOf(
                repositoryProperties to mapOf(
                    "org.gradle.java.installations.paths" to "/opt/jdk-17, /opt/jdk-21,,",
                    "org.gradle.java.installations.auto-detect" to " false",
                ),
                userProperties to mapOf("org.gradle.java.installations.auto-download" to "false"),
            ),
        )

        assertEquals(listOf("/opt/jdk-17", "/opt/jdk-21"), settings.installationPaths)
        assertFalse(settings.autoDetect)
        assertFalse(settings.autoDownload)
    }

    @Test
    fun `only false turns auto-detect off`() {
        assertTrue(read(properties = mapOf(repositoryProperties to mapOf("org.gradle.java.installations.auto-detect" to "true"))).autoDetect)
    }

    @Test
    fun `daemon JVM criteria read their version and whether Gradle can download it`() {
        val criteria = "$repository/gradle/gradle-daemon-jvm.properties"
        val settings = read(
            files = listOf(criteria),
            properties = mapOf(criteria to mapOf("toolchainVersion" to "21 ", "toolchainUrl.MAC_OS.AARCH64" to "https://example.com/jdk")),
        )

        assertEquals(DaemonJvmCriteria(criteria, 21, downloadable = true), settings.daemonCriteria)
    }

    @Test
    fun `daemon JVM criteria without a numeric version or download URLs`() {
        val criteria = "$repository/gradle/gradle-daemon-jvm.properties"
        val settings = read(files = listOf(criteria), properties = mapOf(criteria to mapOf("toolchainVersion" to "latest")))

        assertEquals(DaemonJvmCriteria(criteria, null, downloadable = false), settings.daemonCriteria)
    }

    @Test
    fun `toolchains are read from the root, direct subfolders and convention plugins`() {
        val settings = read(
            texts = mapOf(
                "$repository/build.gradle.kts" to "kotlin { jvmToolchain(21) }",
                "$repository/app/build.gradle" to "java { toolchain { languageVersion = JavaLanguageVersion.of( 17 ) } }",
                "$repository/buildSrc/src/main/kotlin/Conventions.kt" to "jvmToolchain(11)",
                "$repository/build-logic/convention/build.gradle.kts" to "jvmToolchain(21)",
                "$repository/build-logic/convention/build/generated/Generated.kt" to "jvmToolchain(8)",
                "$repository/build-logic/.gradle/cache.kts" to "jvmToolchain(9)",
                "$repository/build-logic/README.md" to "jvmToolchain(10)",
                "$repository/.idea/build.gradle.kts" to "jvmToolchain(12)",
                "$repository/app/src/main/build.gradle.kts" to "jvmToolchain(13)",
            ),
        )

        assertEquals(listOf(11, 17, 21), settings.toolchains)
    }

    @Test
    fun `convention plugins deeper than the search are not read`() {
        val deep = (1..9).joinToString("/") { "level$it" }
        val settings = read(texts = mapOf("$repository/buildSrc/$deep/Conventions.kt" to "jvmToolchain(17)"))

        assertEquals(emptyList(), settings.toolchains)
    }

    @Test
    fun `the foojay resolver in the settings script lets Gradle download toolchains`() {
        val settings = read(texts = mapOf("$repository/settings.gradle.kts" to "plugins { id(\"org.gradle.toolchains.foojay-resolver-convention\") version \"0.8.0\" }"))

        assertTrue(settings.toolchainResolver)
    }

    @Test
    fun `the Android Gradle Plugin version from a version catalog reference`() {
        val catalog = """
            [versions]
            kotlin = "2.0.21"

            agp = "8.5.2"
            [libraries]
            core-ktx = { module = "androidx.core:core-ktx", version = "1.13.1" }
            [plugins]
            android-library = { id = "com.android.library", version.ref = "missing" }
            android-application = { id = "com.android.application", version.ref = "agp" }
        """.trimIndent()

        assertEquals("8.5.2", read(texts = mapOf("$repository/gradle/libs.versions.toml" to catalog)).androidGradlePlugin)
    }

    @Test
    fun `the Android Gradle Plugin version written in the catalog's entry`() {
        val inline = "[plugins]\nandroid-application = { id = \"com.android.application\", version = \"8.7.0\" }"
        val coordinates = "[libraries]\nandroid-gradle = \"com.android.tools.build:gradle:8.2.0\""
        val groupAndName = "[versions]\nagp = \"7.4.2\"\n[libraries]\nandroid-gradle = { group = \"com.android.tools.build\", name = \"gradle\", version.ref = \"agp\" }"

        assertEquals("8.7.0", read(texts = mapOf("$repository/gradle/libs.versions.toml" to inline)).androidGradlePlugin)
        assertEquals("8.2.0", read(texts = mapOf("$repository/gradle/libs.versions.toml" to coordinates)).androidGradlePlugin)
        assertEquals("7.4.2", read(texts = mapOf("$repository/gradle/libs.versions.toml" to groupAndName)).androidGradlePlugin)
    }

    @Test
    fun `a catalog without the Android Gradle Plugin falls back to the build scripts`() {
        val catalog = "[libraries]\naapt2 = { group = \"com.android.tools.build\", name = \"aapt2\", version = \"8.5.2-11315950\" }"
        val settings = read(
            texts = mapOf(
                "$repository/gradle/libs.versions.toml" to catalog,
                "$repository/build.gradle.kts" to "plugins { id(\"com.android.application\") version \"8.6.1\" apply false }",
            ),
        )

        assertEquals("8.6.1", settings.androidGradlePlugin)
    }

    @Test
    fun `the Android Gradle Plugin version from Groovy build scripts`() {
        val plugin = "plugins { id 'com.android.library' version '8.1.4' apply false }"
        val classpath = "buildscript { dependencies { classpath 'com.android.tools.build:gradle:7.0.4' } }"
        val variable = "buildscript { dependencies { classpath \"com.android.tools.build:gradle:\$agp_version\" } }"

        assertEquals("8.1.4", read(texts = mapOf("$repository/build.gradle" to plugin)).androidGradlePlugin)
        assertEquals("7.0.4", read(texts = mapOf("$repository/app/build.gradle" to classpath)).androidGradlePlugin)
        assertNull(read(texts = mapOf("$repository/build.gradle" to variable)).androidGradlePlugin)
    }

    @Test
    fun `daemon JVM criteria without toolchainVersion have no version`() {
        val criteria = "$repository/gradle/gradle-daemon-jvm.properties"

        assertNull(read(files = listOf(criteria)).daemonCriteria?.version)
    }

    @Test
    fun `catalog entries without a usable version are skipped`() {
        val catalog = """
            [versions]
            kotlin = "2.0.21"
            [plugins]
            android-application = { id = "com.android.application" }
            android-library = { id = "com.android.library", version.ref = "missing" }
        """.trimIndent()

        assertNull(read(texts = mapOf("$repository/gradle/libs.versions.toml" to catalog)).androidGradlePlugin)
    }
}
