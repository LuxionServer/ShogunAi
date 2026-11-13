package app.luxion.shogunai.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolVersionsTest {

    @Test
    fun `wrapper distribution URLs give their version`() {
        assertEquals("8.10.2", ToolVersions.gradleDistribution("https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"))
        assertEquals("9.0-rc-1", ToolVersions.gradleDistribution("https://services.gradle.org/distributions/gradle-9.0-rc-1-all.zip"))
        assertEquals("3.9.9", ToolVersions.mavenDistribution("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"))
        assertNull(ToolVersions.gradleDistribution(null))
        assertNull(ToolVersions.mavenDistribution(null))
        assertNull(ToolVersions.gradleDistribution("https://example.com/custom.zip"))
        assertNull(ToolVersions.mavenDistribution("https://example.com/custom.zip"))
    }

    @Test
    fun `printed versions are read`() {
        assertEquals("8.10.2", ToolVersions.gradlePrinted("\n-----\nGradle 8.10.2\n-----\n"))
        assertEquals("3.9.9", ToolVersions.mavenPrinted("Apache Maven 3.9.9 (8e8579a9)"))
        assertEquals("2.29.1", ToolVersions.composePrinted("Docker Compose version v2.29.1-desktop.1"))
        assertEquals("1.29.2", ToolVersions.composePrinted("docker-compose version 1.29.2, build 5becea4c"))
        assertNull(ToolVersions.gradlePrinted("Welcome to Gradle"))
        assertNull(ToolVersions.mavenPrinted("unexpected"))
        assertNull(ToolVersions.composePrinted("Docker Compose version dev"))
    }

    @Test
    fun `java's major version is read in every format`() {
        assertEquals(21, ToolVersions.javaMajor("openjdk version \"21.0.5\" 2024-10-15"))
        assertEquals(8, ToolVersions.javaMajor("java version \"1.8.0_412\""))
        assertEquals(26, ToolVersions.javaMajor("openjdk version \"26-ea\" 2026-03-17"))
        assertEquals(1, ToolVersions.javaMajor("java version \"1\""))
        assertNull(ToolVersions.javaMajor("unexpected"))
    }

    @Test
    fun `numeric prefix of a version`() {
        assertEquals("9.0", ToolVersions.numeric("9.0-rc-1"))
        assertNull(ToolVersions.numeric("nightly"))
    }

    @Test
    fun `Java needed by each Gradle and Maven version`() {
        assertEquals(8, JavaCompatibility.minimumJavaForGradle("8.14.3"))
        assertEquals(17, JavaCompatibility.minimumJavaForGradle("9.0"))
        assertEquals(17, JavaCompatibility.minimumJavaForMaven("4.0.0"))
        assertEquals(8, JavaCompatibility.minimumJavaForMaven("3.9.0"))
        assertEquals(7, JavaCompatibility.minimumJavaForMaven("3.8.8"))
    }

    @Test
    fun `first Gradle for each Java version`() {
        val expected = mapOf(
            9 to "4.3", 10 to "4.7", 11 to "5.0", 12 to "5.4", 13 to "6.0", 14 to "6.3", 15 to "6.7", 16 to "7.0", 17 to "7.3",
            18 to "7.5", 19 to "7.6", 20 to "8.3", 21 to "8.5", 22 to "8.8", 23 to "8.10", 24 to "8.14", 25 to "9.1",
        )

        assertEquals(expected, (5..30).mapNotNull { java -> JavaCompatibility.firstGradleFor(java)?.let { java to it } }.toMap())
    }

    @Test
    fun `Java major version from a JDK's release file`() {
        assertEquals(21, ToolVersions.javaReleaseMajor("IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_VERSION=\"21.0.5\"\n"))
        assertEquals(8, ToolVersions.javaReleaseMajor("JAVA_VERSION=\"1.8.0_412\""))
        assertEquals(25, ToolVersions.javaReleaseMajor("JAVA_VERSION=\"25\""))
        assertNull(ToolVersions.javaReleaseMajor("JAVA_RUNTIME_VERSION=\"21.0.5+11\""))
    }

    @Test
    fun `Java needed by each Android Gradle Plugin version`() {
        assertEquals(17, JavaCompatibility.minimumJavaForAndroidGradlePlugin("9.0.0"))
        assertEquals(17, JavaCompatibility.minimumJavaForAndroidGradlePlugin("8.5.0-alpha01"))
        assertEquals(11, JavaCompatibility.minimumJavaForAndroidGradlePlugin("7.4.2"))
        assertEquals(8, JavaCompatibility.minimumJavaForAndroidGradlePlugin("4.2.2"))
        assertEquals(8, JavaCompatibility.minimumJavaForAndroidGradlePlugin("unknown"))
    }
}
