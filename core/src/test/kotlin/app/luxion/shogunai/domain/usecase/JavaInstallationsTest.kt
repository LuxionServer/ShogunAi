package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.HostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaInstallationsTest {

    private val home = "/home/dev"
    private val gradleUserHome = "/home/dev/.gradle"

    private fun search(
        releases: Map<String, String>,
        os: HostOs = HostOs.MACOS,
        settings: GradleJavaSettings = GradleJavaSettings(),
        variables: Map<String, String> = emptyMap(),
        client: InstalledJava? = null,
        home: String = this.home,
        gradleUserHome: String = this.gradleUserHome,
    ): JavaSearch {
        val fileManager = FakeFileManager()
        releases.forEach { (path, version) -> fileManager.writeText(path, "JAVA_VERSION=\"$version\"") }
        return JavaInstallations(fileManager, { variables[it] }, os, home).search(settings, gradleUserHome, client)
    }

    @Test
    fun `finds the JDKs where Gradle detects them on macOS`() {
        val result = search(
            releases = mapOf(
                "/opt/jdk-11/release" to "11.0.24",
                "/opt/jdk-25/release" to "25",
                "$gradleUserHome/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.12+7/Contents/Home/release" to "17.0.12",
                "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/release" to "21.0.5",
                "$home/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/release" to "1.8.0_412",
                "$home/.sdkman/candidates/java/22.0.2-tem/release" to "22.0.2",
                "$home/.asdf/installs/java/temurin-23/release" to "23.0.1",
                "$home/.jdks/corretto-24/release" to "24.0.1",
            ),
            settings = GradleJavaSettings(installationPaths = listOf("/opt/jdk-25")),
            variables = mapOf("JAVA_HOME" to "/opt/jdk-11"),
            client = InstalledJava("/usr/bin/java", 26),
        )

        assertEquals(
            listOf(
                InstalledJava("/opt/jdk-25", 25),
                InstalledJava("/opt/jdk-11", 11),
                InstalledJava("$gradleUserHome/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.12+7/Contents/Home", 17),
                InstalledJava("$home/.jdks/corretto-24", 24),
                InstalledJava("$home/.sdkman/candidates/java/22.0.2-tem", 22),
                InstalledJava("$home/.asdf/installs/java/temurin-23", 23),
                InstalledJava("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home", 21),
                InstalledJava("$home/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home", 8),
                InstalledJava("/usr/bin/java", 26),
            ),
            result.found,
        )
        assertEquals(
            listOf(
                "/opt/jdk-25",
                "/opt/jdk-11",
                "$gradleUserHome/jdks",
                "$home/.jdks",
                "$home/.sdkman/candidates/java",
                "$home/.asdf/installs/java",
                "/Library/Java/JavaVirtualMachines",
                "$home/Library/Java/JavaVirtualMachines",
            ),
            result.searched,
        )
        assertEquals(InstalledJava("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home", 21), result.find(21))
        assertNull(result.find(19))
    }

    @Test
    fun `searches the system's JDK folder on Linux`() {
        val result = search(releases = mapOf("/usr/lib/jvm/java-21-openjdk-amd64/release" to "21.0.5"), os = HostOs.LINUX, variables = mapOf("JAVA_HOME" to " "))

        assertEquals(listOf(InstalledJava("/usr/lib/jvm/java-21-openjdk-amd64", 21)), result.found)
        assertEquals("/usr/lib/jvm", result.searched.last())
    }

    @Test
    fun `searches the common install folders on Windows`() {
        val result = search(
            releases = mapOf("C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.12.7-hotspot\\release" to "17.0.12"),
            os = HostOs.WINDOWS,
            home = "C:\\Users\\dev",
            gradleUserHome = "C:\\Users\\dev\\.gradle\\",
        )

        assertEquals(listOf(InstalledJava("C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.12.7-hotspot", 17)), result.found)
        assertEquals(
            listOf(
                "C:\\Users\\dev\\.gradle\\jdks",
                "C:\\Users\\dev\\.jdks",
                "C:\\Users\\dev\\.sdkman\\candidates\\java",
                "C:\\Users\\dev\\.asdf\\installs\\java",
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
            ),
            result.searched,
        )
    }

    @Test
    fun `with auto-detect off only the installation paths and the client's Java count`() {
        val result = search(
            releases = mapOf("/usr/lib/jvm/java-21/release" to "21.0.5", "/opt/jdk-17/release" to "17.0.12"),
            os = HostOs.LINUX,
            settings = GradleJavaSettings(installationPaths = listOf("/opt/jdk-17"), autoDetect = false),
            variables = mapOf("JAVA_HOME" to "/usr/lib/jvm/java-21"),
            client = InstalledJava("/usr/bin/java", 11),
        )

        assertEquals(listOf(InstalledJava("/opt/jdk-17", 17), InstalledJava("/usr/bin/java", 11)), result.found)
        assertEquals(listOf("/opt/jdk-17"), result.searched)
    }

    @Test
    fun `a release file without a Java version doesn't count`() {
        val fileManager = FakeFileManager()
        fileManager.writeText("/usr/lib/jvm/broken/release", "IMPLEMENTOR=\"Nobody\"")

        val result = JavaInstallations(fileManager, { null }, HostOs.LINUX, home).search(GradleJavaSettings(), gradleUserHome, null)

        assertEquals(emptyList(), result.found)
    }
}
