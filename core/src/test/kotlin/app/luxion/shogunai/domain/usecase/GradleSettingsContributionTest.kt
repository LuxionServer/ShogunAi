package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.GradleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradleSettingsContributionTest {

    private val repository = "/home/dev/projects/main-repo"
    private val gradleUserHome = "/home/dev/.gradle"

    @Test
    fun `replaces the heap limit and keeps the other arguments`() {
        assertEquals(
            "-Xmx2g -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=512m",
            gradleDaemonArguments("-Xmx6g  -Dfile.encoding=UTF-8 -XX:MaxMetaspaceSize=512m", "2g"),
        )
    }

    @Test
    fun `removes every heap limit`() {
        assertEquals("-Xmx2g -Dfile.encoding=UTF-8", gradleDaemonArguments("-Xmx4g -Dfile.encoding=UTF-8 -Xmx6g", "2g"))
    }

    @Test
    fun `uses only the heap limit without arguments or with quotes`() {
        assertEquals("-Xmx2g", gradleDaemonArguments(null, "2g"))
        assertEquals("-Xmx2g", gradleDaemonArguments("-Xmx6g -Dname=\"a b\"", "2g"))
    }

    @Test
    fun `builds GRADLE_OPTS for each setting alone`() {
        assertEquals(
            "-Dorg.gradle.jvmargs=\"-Xmx2g -Dfile.encoding=UTF-8\"",
            gradleOpts(GradleProvider(daemonMaxHeap = "2g"), "-Xmx6g -Dfile.encoding=UTF-8"),
        )
        assertEquals("-Dorg.gradle.daemon.idletimeout=1800000", gradleOpts(GradleProvider(idleTimeoutMinutes = 30), null))
        assertEquals(
            "-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx1g",
            gradleOpts(GradleProvider(kotlinDaemonMaxHeap = "1g"), null),
        )
    }

    @Test
    fun `builds GRADLE_OPTS with every setting`() {
        assertEquals(
            "-Dorg.gradle.jvmargs=\"-Xmx2g\" -Dorg.gradle.daemon.idletimeout=300000 " +
                "-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx512m",
            gradleOpts(GradleProvider("2g", 5, "512m"), null),
        )
    }

    @Test
    fun `reads the user's arguments before the repository's`() {
        val both = FakeFileManager(
            properties = mapOf(
                "$gradleUserHome/gradle.properties" to mapOf("org.gradle.jvmargs" to "-Xmx3g -Duser=1"),
                "$repository/gradle.properties" to mapOf("org.gradle.jvmargs" to "-Xmx6g -Drepo=1"),
            ),
        )
        assertEquals("-Xmx3g -Duser=1", effectiveGradleJvmArgs(both, gradleUserHome, repository))

        val repositoryOnly = FakeFileManager(
            properties = mapOf("$repository/gradle.properties" to mapOf("org.gradle.jvmargs" to " -Xmx6g -Drepo=1 ")),
        )
        assertEquals("-Xmx6g -Drepo=1", effectiveGradleJvmArgs(repositoryOnly, "$gradleUserHome/", repository))

        assertNull(effectiveGradleJvmArgs(FakeFileManager(), gradleUserHome, repository))
    }

    @Test
    fun `reads the nested build's arguments when the main folder isn't a build`() {
        val fileManager = FakeFileManager(
            existing = listOf("$repository/android/settings.gradle.kts"),
            directories = setOf("$repository/android"),
            properties = mapOf("$repository/android/gradle.properties" to mapOf("org.gradle.jvmargs" to "-Xmx8G -XX:MaxMetaspaceSize=4G")),
        )

        val effective = effectiveGradleJvmArgs(fileManager, gradleUserHome, repository)

        assertEquals("-Xmx2g -XX:MaxMetaspaceSize=4G", gradleDaemonArguments(effective, "2g"))
    }
    @Test
    fun `blank arguments are skipped and leading spaces add no empty argument`() {
        val blank = FakeFileManager(properties = mapOf("$gradleUserHome/gradle.properties" to mapOf("org.gradle.jvmargs" to "  ")))

        assertNull(effectiveGradleJvmArgs(blank, gradleUserHome, repository))
        assertEquals("-Xmx2g -Duser=1", gradleDaemonArguments("  -Duser=1", "2g"))
    }
}
