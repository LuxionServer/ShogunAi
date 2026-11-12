package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.AgentToolId
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.JavaSource
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallationReportTextTest {

    private val settings = "/home/dev/.claude/settings.json"

    private fun summary(state: CheckState, finding: InstallationFinding) = InstallationReportText.summary(InstallationCheck(state, finding))

    private fun fix(state: CheckState, finding: InstallationFinding) = InstallationReportText.fix(InstallationCheck(state, finding))

    @Test
    fun `git texts`() {
        val passed = InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")
        assertEquals("Git 2.45.1 (/usr/bin/git)", summary(CheckState.PASSED, passed))
        assertNull(fix(CheckState.PASSED, passed))

        val old = InstallationFinding.Git("/usr/bin/git", "2.25.1", "2.29")
        assertEquals("Git 2.25.1 at /usr/bin/git is older than 2.29, which ShogunAi needs", summary(CheckState.FAILED, old))
        assertEquals("Update Git to 2.29 or later (https://git-scm.com/downloads)", fix(CheckState.FAILED, old))

        val missing = InstallationFinding.Git(null, null, "2.29")
        assertEquals("Git not found on the PATH", summary(CheckState.FAILED, missing))
        assertEquals("Install Git 2.29 or later (https://git-scm.com/downloads) and make sure it's on your PATH", fix(CheckState.FAILED, missing))

        val unreadable = InstallationFinding.Git("/usr/bin/git", null, "2.29", "permission denied")
        assertEquals("Couldn't read Git's version from /usr/bin/git: permission denied", summary(CheckState.FAILED, unreadable))
        assertEquals("Check that /usr/bin/git runs, or install Git 2.29 or later (https://git-scm.com/downloads)", fix(CheckState.FAILED, unreadable))

        assertEquals("Couldn't look for Git: boom", summary(CheckState.FAILED, InstallationFinding.Git(null, null, "2.29", "boom")))
    }

    @Test
    fun `PATH texts`() {
        assertEquals("PATH read from your login shell (/bin/zsh)", summary(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.LOGIN_SHELL, "/bin/zsh")))
        assertEquals("PATH of the process that started ShogunAi", summary(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.PROCESS, "/bin/zsh")))
        val fallback = InstallationFinding.PathSource(HostPathSource.FALLBACK, "/bin/zsh")
        assertEquals("Couldn't read your login shell's PATH (/bin/zsh), so the usual install folders were used", summary(CheckState.WARNING, fallback))
        assertEquals("Make /bin/zsh start without errors or prompts within 5 seconds, then reopen ShogunAi", fix(CheckState.WARNING, fallback))
        val noShell = InstallationFinding.PathSource(HostPathSource.FALLBACK, null)
        assertEquals("Couldn't read your login shell's PATH (SHELL isn't set), so the usual install folders were used", summary(CheckState.WARNING, noShell))
        assertEquals("Make your login shell start without errors or prompts within 5 seconds, then reopen ShogunAi", fix(CheckState.WARNING, noShell))
    }

    @Test
    fun `shogun command texts`() {
        val launcher = "/home/dev/.local/bin/shogun"
        val folder = "/home/dev/.local/bin"
        assertEquals("shogun command at $launcher", summary(CheckState.PASSED, InstallationFinding.ShogunCommand(CommandLineToolState.AVAILABLE, launcher, folder, false)))

        val offPath = InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, launcher, folder, false)
        assertEquals("shogun is at $launcher, but $folder isn't on the PATH", summary(CheckState.WARNING, offPath))
        assertEquals("Add $folder to your PATH", fix(CheckState.WARNING, offPath))
        assertEquals(
            "Restart your terminal and Claude Code so they pick up $folder",
            fix(CheckState.WARNING, offPath.copy(restartNeeded = true)),
        )

        val broken = InstallationFinding.ShogunCommand(CommandLineToolState.BROKEN, launcher, folder, false)
        assertEquals("The shogun command at $launcher points to a ShogunAi app that no longer exists", summary(CheckState.FAILED, broken))
        assertEquals("Install the command again from the desktop app's Preferences › Claude Code", fix(CheckState.FAILED, broken))

        val missing = InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false)
        assertEquals("shogun command not found", summary(CheckState.FAILED, missing))
        assertEquals("Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide", fix(CheckState.FAILED, missing))
    }

    @Test
    fun `hooks texts`() {
        val installed = InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED)
        assertEquals("Claude Code hooks installed in $settings", summary(CheckState.PASSED, installed))

        val missing = InstallationFinding.Hooks(settings, HooksInstallState.MISSING)
        assertEquals("Claude Code hooks aren't installed in $settings", summary(CheckState.WARNING, missing))
        assertEquals("Run 'shogun hooks install', or install them from the desktop app's Preferences › Claude Code", fix(CheckState.WARNING, missing))

        val outdated = InstallationFinding.Hooks(settings, HooksInstallState.OUTDATED, listOf("SessionStart", "Stop"))
        assertEquals("Claude Code hooks are out of date in $settings (SessionStart, Stop)", summary(CheckState.WARNING, outdated))

        val noShogun = InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.MISSING)
        assertEquals("Claude Code hooks run 'shogun', which isn't installed", summary(CheckState.FAILED, noShogun))
        assertEquals(
            "The hooks run 'shogun'. Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide",
            fix(CheckState.FAILED, noShogun),
        )
        assertEquals(
            "Claude Code hooks run 'shogun', whose launcher no longer exists",
            summary(CheckState.FAILED, noShogun.copy(shogun = CommandLineToolState.BROKEN)),
        )
        assertEquals(
            "Claude Code hooks run 'shogun', which isn't on the PATH",
            summary(CheckState.WARNING, noShogun.copy(shogun = CommandLineToolState.INSTALLED_NOT_ON_PATH)),
        )

        val invalid = InstallationFinding.Hooks(settings, null, invalidDetail = "Unexpected JSON token")
        assertEquals("Couldn't read Claude Code's settings $settings: Unexpected JSON token", summary(CheckState.FAILED, invalid))
        assertEquals("Correct $settings so it's valid JSON, then check again", fix(CheckState.FAILED, invalid))
    }

    @Test
    fun `agent tool texts`() {
        assertEquals("Herdr at /opt/homebrew/bin/herdr", summary(CheckState.PASSED, InstallationFinding.AgentTool(AgentToolId.HERDR, "/opt/homebrew/bin/herdr")))
        val orca = InstallationFinding.AgentTool(AgentToolId.ORCA, null)
        assertEquals("Orca not found", summary(CheckState.NOT_FOUND, orca))
        assertEquals("Install Orca if you use it: https://www.onorca.dev", fix(CheckState.NOT_FOUND, orca))
    }

    @Test
    fun `project texts`() {
        val base = "/home/dev/projects/main-repo"
        assertEquals("main-repo: 1 local file available in $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 1, emptyList())))
        assertEquals("main-repo: no local files to copy or link from $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList())))
        assertEquals("main-repo: 3 local files available in $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 3, emptyList())))

        val gone = InstallationFinding.ProjectFiles("main-repo", base, true, 0, emptyList())
        assertEquals("main-repo: base folder $base doesn't exist", summary(CheckState.FAILED, gone))
        assertEquals("Restore $base, or remove the project from ShogunAi", fix(CheckState.FAILED, gone))

        val broken = UnavailableLocalFile("keys.properties", "$base/keys.properties", LocalFileOrigin.REPOSITORY, UnavailableReason.BrokenLink("/missing/keys.properties"))
        val missing = UnavailableLocalFile("local.properties", "$base/local.properties", LocalFileOrigin.PROJECT, UnavailableReason.Missing)
        val unavailable = InstallationFinding.ProjectFiles("main-repo", base, false, 2, listOf(broken, missing))
        assertEquals("main-repo: 2 local files unavailable in $base", summary(CheckState.FAILED, unavailable))
        assertEquals(
            "keys.properties: $base/keys.properties is a link to /missing/keys.properties, which doesn't exist. " +
                "Restore /missing/keys.properties or replace the link with the file, or stop matching it in .shogunai.json or .worktreeinclude\n" +
                "local.properties: not found at $base/local.properties. Create it there, or remove it from the project's local files",
            fix(CheckState.FAILED, unavailable),
        )

        val unresolved = InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList(), "not a git repository")
        assertEquals("main-repo: couldn't check its local files: not a git repository", summary(CheckState.FAILED, unresolved))
        assertEquals("Check that $base is a Git repository you can read", fix(CheckState.FAILED, unresolved))
    }

    @Test
    fun `lines put the fix under its check`() {
        val report = InstallationReport(
            listOf(
                InstallationCheck(CheckState.PASSED, InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")),
                InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.MISSING)),
                InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.AgentTool(AgentToolId.ORCA, null)),
                InstallationCheck(
                    CheckState.FAILED,
                    InstallationFinding.ProjectFiles(
                        "main-repo", "/p", false, 2,
                        listOf(
                            UnavailableLocalFile("a", "/p/a", LocalFileOrigin.PROJECT, UnavailableReason.Missing),
                            UnavailableLocalFile("b", "/p/b", LocalFileOrigin.PROJECT, UnavailableReason.Unreadable(null)),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "ok    Git 2.45.1 (/usr/bin/git)",
                "warn  Claude Code hooks aren't installed in $settings",
                "      fix: Run 'shogun hooks install', or install them from the desktop app's Preferences › Claude Code",
                "--    Orca not found",
                "      fix: Install Orca if you use it: https://www.onorca.dev",
                "fail  main-repo: 2 local files unavailable in /p",
                "      fix: a: not found at /p/a. Create it there, or remove it from the project's local files",
                "           b: /p/b can't be read. Make it readable for your user, or remove it from the project's local files",
            ),
            InstallationReportText.lines(report),
        )
    }

    @Test
    fun `markers name every state`() {
        assertTrue(CheckState.entries.map(InstallationReportText::marker).distinct().size == CheckState.entries.size)
    }

    @Test
    fun `details are added only when there is one`() {
        assertEquals("Couldn't read Git's version from /usr/bin/git", summary(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", null, "2.29")))
        assertEquals("Couldn't read Git's version from /usr/bin/git", summary(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", null, "2.29", " ")))
    }

    @Test
    fun `shogun fixes name its folder or say its folder`() {
        val unknownFolder = InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/opt/shogun", null, true)

        assertEquals("Restart your terminal and Claude Code so they pick up its folder", fix(CheckState.WARNING, unknownFolder))
        assertEquals(
            "The hooks run 'shogun'. Add its folder to your PATH",
            fix(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.INSTALLED_NOT_ON_PATH)),
        )
        assertEquals(
            "The hooks run 'shogun'. Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide",
            fix(CheckState.FAILED, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.AVAILABLE)),
        )
    }

    private fun tool(tool: ProviderToolId, location: String? = null, problem: ProviderToolProblem? = null, installed: Int? = null, version: String? = null) =
        InstallationFinding.ProviderTool("main-repo", tool, location, problem, installed, version)

    @Test
    fun `provider runtime texts`() {
        assertEquals("main-repo: Gradle 8.10.2 at /repo/gradlew", summary(CheckState.PASSED, tool(ProviderToolId.GRADLE, "/repo/gradlew", version = "8.10.2")))
        assertEquals("main-repo: Java for Gradle 21 at /usr/bin/java", summary(CheckState.PASSED, tool(ProviderToolId.GRADLE_JAVA, "/usr/bin/java", version = "21")))
        assertEquals("main-repo: Docker daemon 27.3.1 is running", summary(CheckState.PASSED, tool(ProviderToolId.DOCKER_DAEMON, version = "27.3.1")))

        val javaHome = tool(ProviderToolId.MAVEN_JAVA, problem = ProviderToolProblem.JavaHomeInvalid("/opt/jdk-gone"))
        assertEquals("main-repo: Java for Maven not found: JAVA_HOME is /opt/jdk-gone, which has no bin/java", summary(CheckState.WARNING, javaHome))
        assertTrue(fix(CheckState.WARNING, javaHome)!!.contains("unset it"))

        val tooOld = tool(ProviderToolId.GRADLE_JAVA, "/usr/bin/java", ProviderToolProblem.JavaTooOld(11, 17, "9.1.0"))
        assertEquals("main-repo: Java for Gradle is Java 11 at /usr/bin/java, but Gradle 9.1.0 needs Java 17 or newer", summary(CheckState.WARNING, tooOld))
        assertEquals("Install Java 17 or newer and point JAVA_HOME at it", fix(CheckState.WARNING, tooOld))
        val mavenTooOld = tool(ProviderToolId.MAVEN_JAVA, "/usr/bin/java", ProviderToolProblem.JavaTooOld(11, 17, "4.0.0"))
        assertEquals("main-repo: Java for Maven is Java 11 at /usr/bin/java, but Maven 4.0.0 needs Java 17 or newer", summary(CheckState.WARNING, mavenTooOld))

        val gradleTooOld = tool(ProviderToolId.GRADLE_JAVA, "/usr/bin/java", ProviderToolProblem.GradleTooOld(21, "8.4", "8.5"))
        assertEquals(
            "main-repo: Java for Gradle is Java 21 at /usr/bin/java, which needs Gradle 8.5 or newer, but the project uses Gradle 8.4",
            summary(CheckState.WARNING, gradleTooOld),
        )
        assertEquals("Update the project's Gradle wrapper to 8.5 or newer, or point JAVA_HOME at an older Java", fix(CheckState.WARNING, gradleTooOld))

        val compose = tool(ProviderToolId.DOCKER_COMPOSE, "/usr/bin/docker", ProviderToolProblem.TooOld("1.29.2", "2.0"))
        assertEquals(
            "main-repo: Docker Compose 1.29.2 at /usr/bin/docker is older than 2.0, the first with the 'docker compose ls' ShogunAi uses",
            summary(CheckState.WARNING, compose),
        )
        assertTrue(fix(CheckState.WARNING, compose)!!.contains("Compose 2.0 or newer"))

        val mac = tool(ProviderToolId.DOCKER_DAEMON, problem = ProviderToolProblem.DaemonNotRunning(HostOs.MACOS, "Cannot connect"))
        assertEquals("main-repo: Docker's daemon isn't running: Cannot connect", summary(CheckState.WARNING, mac))
        assertEquals("Start Docker Desktop, or the Docker engine you use, and check again", fix(CheckState.WARNING, mac))
        val windows = tool(ProviderToolId.DOCKER_DAEMON, problem = ProviderToolProblem.DaemonNotRunning(HostOs.WINDOWS, null))
        assertEquals(fix(CheckState.WARNING, mac), fix(CheckState.WARNING, windows))
        val linux = tool(ProviderToolId.DOCKER_DAEMON, problem = ProviderToolProblem.DaemonNotRunning(HostOs.LINUX, null))
        assertTrue(fix(CheckState.WARNING, linux)!!.contains("sudo systemctl start docker"))
    }

    @Test
    fun `Gradle build Java texts when they pass`() {
        val daemon = InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_DAEMON_JAVA, "/opt/jdk-21", version = "21")
        assertEquals("main-repo: Java for Gradle's daemon 21 at /opt/jdk-21", summary(CheckState.PASSED, daemon))
        val downloaded = InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_DAEMON_JAVA, null, version = "21", provisioned = true)
        assertEquals("main-repo: Java for Gradle's daemon 21, which Gradle downloads when it's missing", summary(CheckState.PASSED, downloaded))
        val toolchains = tool(ProviderToolId.GRADLE_TOOLCHAINS, version = "17, 21")
        assertEquals("main-repo: Java toolchains 17, 21 installed", summary(CheckState.PASSED, toolchains))
        val agp = InstallationFinding.ProviderTool("main-repo", ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA, null, version = "8.5.2", java = 17)
        assertEquals("main-repo: Android Gradle Plugin 8.5.2 runs on Java 17", summary(CheckState.PASSED, agp))
    }

    @Test
    fun `Gradle build Java problem texts`() {
        val searched = listOf("/home/dev/.gradle/jdks", "/usr/lib/jvm")
        val criteriaMissing = tool(ProviderToolId.GRADLE_DAEMON_JAVA, problem = ProviderToolProblem.JavaNotInstalled(listOf(21), searched), version = "21")
        assertEquals(
            "main-repo: Java for Gradle's daemon: Java 21 isn't installed (looked in /home/dev/.gradle/jdks, /usr/lib/jvm)",
            summary(CheckState.WARNING, criteriaMissing),
        )
        assertEquals(
            "Install a Java 21 JDK (for example from https://adoptium.net), or add a toolchain download repository and run './gradlew updateDaemonJvm' so Gradle downloads it",
            fix(CheckState.WARNING, criteriaMissing),
        )
        val toolchainsMissing = tool(ProviderToolId.GRADLE_TOOLCHAINS, problem = ProviderToolProblem.JavaNotInstalled(listOf(11, 17), searched))
        assertEquals("main-repo: Java toolchains: Java 11 and 17 aren't installed (looked in /home/dev/.gradle/jdks, /usr/lib/jvm)", summary(CheckState.WARNING, toolchainsMissing))
        assertTrue(fix(CheckState.WARNING, toolchainsMissing)!!.contains("'org.gradle.toolchains.foojay-resolver-convention' plugin"))

        val invalid = tool(ProviderToolId.GRADLE_DAEMON_JAVA, problem = ProviderToolProblem.GradleJavaHomeInvalid("/opt/jdk-gone", "/repo/gradle.properties"))
        assertEquals("main-repo: Java for Gradle's daemon not found: org.gradle.java.home is /opt/jdk-gone in /repo/gradle.properties, which has no bin/java", summary(CheckState.WARNING, invalid))
        assertEquals("Point org.gradle.java.home in /repo/gradle.properties at a JDK's folder (the one with bin/java), or remove it", fix(CheckState.WARNING, invalid))

        val unreadable = tool(ProviderToolId.GRADLE_DAEMON_JAVA, "/repo/gradle/gradle-daemon-jvm.properties", ProviderToolProblem.DaemonCriteriaUnreadable("/repo/gradle/gradle-daemon-jvm.properties"))
        assertEquals("main-repo: Java for Gradle's daemon: couldn't read toolchainVersion in /repo/gradle/gradle-daemon-jvm.properties", summary(CheckState.WARNING, unreadable))
        assertEquals("Regenerate the daemon JVM criteria with './gradlew updateDaemonJvm --jvm-version=<version>'", fix(CheckState.WARNING, unreadable))

        val criteriaTooNew = tool(ProviderToolId.GRADLE_DAEMON_JAVA, problem = ProviderToolProblem.GradleTooOld(25, "8.10", "9.1", JavaSource.DAEMON_CRITERIA))
        assertEquals("main-repo: Java for Gradle's daemon is Java 25, which needs Gradle 9.1 or newer, but the project uses Gradle 8.10", summary(CheckState.WARNING, criteriaTooNew))
        assertEquals("Update the project's Gradle wrapper to 9.1 or newer, or lower toolchainVersion in gradle/gradle-daemon-jvm.properties", fix(CheckState.WARNING, criteriaTooNew))
        val propertyTooNew = tool(ProviderToolId.GRADLE_DAEMON_JAVA, "/opt/jdk-25/bin/java", ProviderToolProblem.GradleTooOld(25, "8.10", "9.1", JavaSource.GRADLE_PROPERTY))
        assertEquals("Update the project's Gradle wrapper to 9.1 or newer, or point org.gradle.java.home at an older Java", fix(CheckState.WARNING, propertyTooNew))

        val propertyTooOld = tool(ProviderToolId.GRADLE_DAEMON_JAVA, "/opt/jdk-11/bin/java", ProviderToolProblem.JavaTooOld(11, 17, "9.1.0", JavaSource.GRADLE_PROPERTY))
        assertEquals("main-repo: Java for Gradle's daemon is Java 11 at /opt/jdk-11/bin/java, but Gradle 9.1.0 needs Java 17 or newer", summary(CheckState.WARNING, propertyTooOld))
        assertEquals("Install Java 17 or newer and point org.gradle.java.home at it", fix(CheckState.WARNING, propertyTooOld))
        val criteriaTooOld = tool(ProviderToolId.GRADLE_DAEMON_JAVA, problem = ProviderToolProblem.JavaTooOld(11, 17, "9.1.0", JavaSource.DAEMON_CRITERIA))
        assertEquals("Raise the daemon's Java to 17 or newer: './gradlew updateDaemonJvm --jvm-version=17'", fix(CheckState.WARNING, criteriaTooOld))

        val agp = tool(ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA, problem = ProviderToolProblem.AndroidGradlePluginNeedsJava(11, 17, "8.2.0", JavaSource.ENVIRONMENT))
        assertEquals("main-repo: Android Gradle Plugin 8.2.0 needs Java 17 or newer, but Gradle's daemon runs Java 11", summary(CheckState.WARNING, agp))
        assertEquals("Install Java 17 or newer and point JAVA_HOME at it", fix(CheckState.WARNING, agp))

        listOf(ProviderToolId.GRADLE_DAEMON_JAVA, ProviderToolId.GRADLE_TOOLCHAINS, ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA).forEach { id ->
            assertTrue(fix(CheckState.WARNING, tool(id, problem = ProviderToolProblem.Unusable("boom")))!!.startsWith("Install a JDK"))
        }
    }

    @Test
    fun `passed provider tool texts`() {
        assertEquals("main-repo: Gradle at /home/dev/projects/main-repo/gradlew", summary(CheckState.PASSED, tool(ProviderToolId.GRADLE, "/home/dev/projects/main-repo/gradlew")))
        assertEquals("main-repo: Android SDK at /opt/android-sdk (2 virtual devices)", summary(CheckState.PASSED, tool(ProviderToolId.ANDROID_SDK, "/opt/android-sdk", installed = 2)))
        assertEquals("main-repo: iOS Simulator (1 iOS runtime)", summary(CheckState.PASSED, tool(ProviderToolId.IOS_SIMULATOR, installed = 1)))
        assertEquals("main-repo: iOS Simulator (0 iOS runtimes)", summary(CheckState.PASSED, tool(ProviderToolId.IOS_SIMULATOR)))
        assertEquals("main-repo: Android SDK at /opt/android-sdk (0 virtual devices)", summary(CheckState.PASSED, tool(ProviderToolId.ANDROID_SDK, "/opt/android-sdk")))
        assertNull(fix(CheckState.PASSED, tool(ProviderToolId.GRADLE, "/usr/bin/gradle")))
    }

    @Test
    fun `provider tool problem texts`() {
        val missing = tool(ProviderToolId.DOCKER_COMPOSE, problem = ProviderToolProblem.NotFound(listOf("/usr/local/bin/docker"), listOf("docker")))
        assertEquals("main-repo: Docker Compose not found, needed by the docker-compose provider (looked in /usr/local/bin/docker, docker on the PATH)", summary(CheckState.WARNING, missing))
        assertEquals("Install Docker with its Compose plugin, for example Docker Desktop: https://docs.docker.com/get-docker/", fix(CheckState.WARNING, missing))

        val unusable = tool(ProviderToolId.XCODE, "/usr/bin/xcodebuild", ProviderToolProblem.Unusable("requires Xcode"))
        assertEquals("main-repo: Xcode at /usr/bin/xcodebuild doesn't work: requires Xcode", summary(CheckState.WARNING, unusable))
        assertEquals("Install Xcode from the App Store and select it: sudo xcode-select -s /Applications/Xcode.app", fix(CheckState.WARNING, unusable))
        assertEquals("main-repo: Flutter doesn't work", summary(CheckState.WARNING, tool(ProviderToolId.FLUTTER, problem = ProviderToolProblem.Unusable(null))))

        val mac = tool(ProviderToolId.IOS_SIMULATOR, problem = ProviderToolProblem.NeedsMacOs)
        assertEquals("main-repo: the ios-simulator provider needs macOS with Xcode", summary(CheckState.WARNING, mac))
        assertEquals("Use the ios-simulator provider on a Mac with Xcode, or ignore this on other systems", fix(CheckState.WARNING, mac))

        val noAvd = tool(ProviderToolId.ANDROID_SDK, "/opt/android-sdk", ProviderToolProblem.NoAndroidVirtualDevice, 0)
        assertEquals("main-repo: Android SDK at /opt/android-sdk has no Android Virtual Device", summary(CheckState.WARNING, noAvd))
        assertEquals("Create a virtual device in Android Studio's Device Manager", fix(CheckState.WARNING, noAvd))

        val noRuntime = tool(ProviderToolId.IOS_SIMULATOR, problem = ProviderToolProblem.NoIosRuntime)
        assertEquals("main-repo: no iOS simulator runtime is installed", summary(CheckState.WARNING, noRuntime))
        assertEquals("Install an iOS runtime from Xcode's Settings › Components", fix(CheckState.WARNING, noRuntime))

        val unreadable = tool(ProviderToolId.PROVIDERS, problem = ProviderToolProblem.ProvidersUnreadable("malformed"))
        assertEquals("main-repo: couldn't read its providers: malformed", summary(CheckState.WARNING, unreadable))
        assertEquals("Correct the repository's .shogunai.json so it's valid", fix(CheckState.WARNING, unreadable))
    }

    @Test
    fun `every provider tool has an install fix`() {
        val fixes = ProviderToolId.entries.associateWith { fix(CheckState.WARNING, tool(it, problem = ProviderToolProblem.NotFound(emptyList()))) }

        assertTrue(fixes.getValue(ProviderToolId.GRADLE)!!.contains("gradlew"))
        assertTrue(fixes.getValue(ProviderToolId.MAVEN)!!.contains("mvnw"))
        assertTrue(fixes.getValue(ProviderToolId.ANDROID_SDK)!!.contains("ANDROID_HOME"))
        assertTrue(fixes.getValue(ProviderToolId.IOS_SIMULATOR)!!.contains("xcode-select"))
        assertTrue(fixes.getValue(ProviderToolId.COCOAPODS)!!.contains("cocoapods.org"))
        assertTrue(fixes.getValue(ProviderToolId.FLUTTER)!!.contains("FVM"))
        assertTrue(fixes.getValue(ProviderToolId.GRADLE_JAVA)!!.contains("JAVA_HOME"))
        assertEquals(fixes.getValue(ProviderToolId.DOCKER_COMPOSE), fixes.getValue(ProviderToolId.DOCKER_DAEMON))
        assertTrue(fixes.getValue(ProviderToolId.PROVIDERS)!!.contains(".shogunai.json"))
    }

    @Test
    fun `multirepo texts`() {
        val folder = "/home/dev/projects/product"
        val healthy = InstallationFinding.MultirepoCheck(folder, "product", listOf("app", "api"), 1, emptyList())
        assertEquals("Multirepo product (/home/dev/projects/product): app, api; 1 group", summary(CheckState.PASSED, healthy))
        assertEquals("Multirepo product (/home/dev/projects/product): app, api; 2 groups", summary(CheckState.PASSED, healthy.copy(groups = 2)))
        assertNull(fix(CheckState.PASSED, healthy))

        val gone = InstallationFinding.MultirepoCheck(folder, "product", emptyList(), 0, listOf(MultirepoProblem.DeclarationMissing))
        assertEquals("Multirepo product (/home/dev/projects/product): .shogunai-multirepo.json is gone", summary(CheckState.WARNING, gone))
        assertEquals("Restore /home/dev/projects/product; otherwise ShogunAi forgets it the next time it starts", fix(CheckState.WARNING, gone))

        val broken = healthy.copy(
            problems = listOf(
                MultirepoProblem.InvalidDeclaration("$folder/.shogunai-multirepo.json", "worktreesRoot is missing"),
                MultirepoProblem.RepositoryMissing("api", "$folder/product-api"),
                MultirepoProblem.NotTrusted,
                MultirepoProblem.ListingFailed("app", "boom"),
                MultirepoProblem.GroupWorktreeMissing("feature/TASK-123", "app", "/home/dev/worktrees/app/TASK-123"),
            ),
        )
        assertEquals("Multirepo product (/home/dev/projects/product): 5 problems", summary(CheckState.FAILED, broken))
        assertEquals(
            listOf(
                "/home/dev/projects/product/.shogunai-multirepo.json: worktreesRoot is missing; correct it",
                "'api': /home/dev/projects/product/product-api isn't a Git repository; restore it, or change its path in /home/dev/projects/product/.shogunai-multirepo.json",
                "It isn't trusted on this machine; review it and run 'shogun multirepo trust /home/dev/projects/product'",
                "Couldn't list the worktrees of 'app': boom; check it's a Git repository you can read",
                "Group feature/TASK-123: the 'app' worktree's folder /home/dev/worktrees/app/TASK-123 doesn't exist; restore it, or run 'git worktree prune' in 'app'",
            ),
            fix(CheckState.FAILED, broken)!!.lines(),
        )
        assertEquals("Multirepo product (/home/dev/projects/product): 1 problem", summary(CheckState.WARNING, healthy.copy(problems = listOf(MultirepoProblem.NotTrusted))))
    }

    @Test
    fun `a provider tool without a problem has no fix`() {
        assertNull(fix(CheckState.WARNING, tool(ProviderToolId.GRADLE, "/home/dev/projects/main-repo/gradlew")))
    }
}
