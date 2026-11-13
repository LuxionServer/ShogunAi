package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class HeavyBuildCommandTest {

    private fun flagged(command: String) = HeavyBuildCommand.unwrappedBuilds(command)

    @Test
    fun `heavy builds of every tool are flagged`() {
        assertEquals(listOf("./gradlew test koverVerify"), flagged("./gradlew test koverVerify"))
        assertEquals(listOf("gradle build"), flagged("gradle build"))
        assertEquals(listOf("mvn -q verify"), flagged("mvn -q verify"))
        assertEquals(listOf("./mvnw package"), flagged("./mvnw package"))
        assertEquals(listOf("xcodebuild -scheme App test"), flagged("xcodebuild -scheme App test"))
        assertEquals(listOf("swift build"), flagged("swift build"))
        assertEquals(listOf("swift -v test"), flagged("swift -v test"))
        assertEquals(listOf("./gradlew"), flagged("./gradlew"))
    }

    @Test
    fun `only the build segment of a compound command is flagged`() {
        assertEquals(listOf("mvn -q verify"), flagged("cd app && mvn -q verify"))
        assertEquals(
            listOf("./gradlew koverVerify"),
            flagged("shogun build run -- ./gradlew test && ./gradlew koverVerify | tail -5"),
        )
        assertEquals(listOf("./gradlew test"), flagged("(./gradlew test)"))
    }

    @Test
    fun `launchers, assignments, paths, quotes and Windows suffixes are seen through`() {
        assertEquals(listOf("JAVA_HOME=/opt/jdk time nice ./gradlew test"), flagged("JAVA_HOME=/opt/jdk time nice ./gradlew test"))
        assertEquals(listOf("env nohup command exec /usr/local/bin/mvn install"), flagged("env nohup command exec /usr/local/bin/mvn install"))
        assertEquals(listOf("\"./gradlew\" 'test'"), flagged("\"./gradlew\" 'test'"))
        assertEquals(listOf("gradlew.bat build"), flagged("gradlew.bat build"))
        assertEquals(listOf("C:\\tools\\mvn.cmd package"), flagged("C:\\tools\\mvn.cmd package"))
        assertEquals(listOf("GRADLEW.EXE build"), flagged("GRADLEW.EXE build"))
    }

    @Test
    fun `wrapped builds aren't flagged`() {
        assertEquals(emptyList(), flagged("shogun build run -- xcodebuild -scheme App test"))
        assertEquals(emptyList(), flagged("FOO=1 shogun build run --wait-timeout 5 -- ./gradlew test"))
    }

    @Test
    fun `light invocations aren't flagged`() {
        listOf(
            "./gradlew --stop",
            "./gradlew --status",
            "gradle --version",
            "gradle -v",
            "./gradlew help",
            "./gradlew -q tasks help",
            "mvn --version",
            "./mvnw -v",
            "xcodebuild -version",
            "xcodebuild -list",
            "xcodebuild -showsdks",
            "xcodebuild -showdestinations -scheme App",
            "xcodebuild -showBuildSettings",
            "swift package resolve",
            "swift --version",
        ).forEach { assertEquals(emptyList(), flagged(it), it) }
    }

    @Test
    fun `commands that only mention a tool aren't flagged`() {
        assertEquals(emptyList(), flagged("grep -r gradle docs"))
        assertEquals(emptyList(), flagged("echo xcodebuild"))
        assertEquals(emptyList(), flagged("cat build.gradle.kts"))
        assertEquals(emptyList(), flagged("FOO=1"))
        assertEquals(emptyList(), flagged(""))
        assertEquals(emptyList(), flagged("time"))
    }
}
