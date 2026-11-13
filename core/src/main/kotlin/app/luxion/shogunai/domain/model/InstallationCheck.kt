package app.luxion.shogunai.domain.model

enum class CheckState { PASSED, WARNING, FAILED, NOT_FOUND }

enum class HostPathSource { LOGIN_SHELL, PROCESS, FALLBACK }

enum class CommandLineToolState { AVAILABLE, INSTALLED_NOT_ON_PATH, BROKEN, MISSING }

enum class HooksInstallState { INSTALLED, OUTDATED, MISSING }

enum class AgentToolId(val id: String, val displayName: String, val website: String) {
    CLAUDE_CODE("claude-code", "Claude Code", "https://docs.claude.com/en/docs/claude-code/setup"),
    HERDR("herdr", "Herdr", "https://herdr.dev"),
    ORCA("orca", "Orca", "https://www.onorca.dev"),
}

enum class ProviderToolId(val id: String, val displayName: String, val provider: String) {
    PROVIDERS("providers", "Providers", ""),
    GRADLE("gradle", "Gradle", "gradle"),
    GRADLE_JAVA("gradle-java", "Java for Gradle", "gradle"),
    MAVEN("maven", "Maven", "maven"),
    GRADLE_DAEMON_JAVA("gradle-daemon-java", "Java for Gradle's daemon", "gradle"),
    GRADLE_TOOLCHAINS("gradle-toolchains", "Java toolchains", "gradle"),
    ANDROID_GRADLE_PLUGIN_JAVA("agp-java", "Java for the Android Gradle Plugin", "gradle"),
    MAVEN_JAVA("maven-java", "Java for Maven", "maven"),
    ANDROID_SDK("android-sdk", "Android SDK", "emulator"),
    IOS_SIMULATOR("ios-simulator", "iOS Simulator", "ios-simulator"),
    XCODE("xcode", "Xcode", "xcode"),
    COCOAPODS("cocoapods", "CocoaPods", "xcode"),
    DOCKER_COMPOSE("docker-compose", "Docker Compose", "docker-compose"),
    DOCKER_DAEMON("docker-daemon", "Docker daemon", "docker-compose"),
    FLUTTER("flutter", "Flutter", "flutter"),
    NODE("node", "Node.js", "node"),
    NPM("npm", "npm", "node"),
    PNPM("pnpm", "pnpm", "node"),
    YARN("yarn", "Yarn", "node"),
    BUN("bun", "bun", "node"),
}

sealed interface ProviderToolProblem {
    data class NotFound(val searched: List<String>, val commands: List<String> = emptyList()) : ProviderToolProblem
    data class Unusable(val detail: String?) : ProviderToolProblem
    data object NeedsMacOs : ProviderToolProblem
    data object NoAndroidVirtualDevice : ProviderToolProblem
    data object NoIosRuntime : ProviderToolProblem
    data class ProvidersUnreadable(val detail: String?) : ProviderToolProblem
    data class JavaHomeInvalid(val javaHome: String) : ProviderToolProblem
    data class JavaTooOld(val java: Int, val required: Int, val toolVersion: String, val source: JavaSource = JavaSource.ENVIRONMENT) :
        ProviderToolProblem
    data class GradleTooOld(val java: Int, val gradle: String, val required: String, val source: JavaSource = JavaSource.ENVIRONMENT) :
        ProviderToolProblem
    data class JavaNotInstalled(val versions: List<Int>, val searched: List<String>) : ProviderToolProblem
    data class GradleJavaHomeInvalid(val javaHome: String, val propertiesFile: String) : ProviderToolProblem
    data class DaemonCriteriaUnreadable(val path: String) : ProviderToolProblem
    data class AndroidGradlePluginNeedsJava(val java: Int, val required: Int, val agp: String, val source: JavaSource) : ProviderToolProblem
    data class TooOld(val version: String, val minimum: String) : ProviderToolProblem
    data class DaemonNotRunning(val os: HostOs, val detail: String?) : ProviderToolProblem
}

enum class JavaSource { ENVIRONMENT, GRADLE_PROPERTY, DAEMON_CRITERIA }

sealed interface MultirepoProblem {
    val blocking: Boolean get() = false

    data object DeclarationMissing : MultirepoProblem

    data class InvalidDeclaration(val path: String, val detail: String?) : MultirepoProblem {
        override val blocking: Boolean get() = true
    }

    data class RepositoryMissing(val alias: String, val path: String) : MultirepoProblem {
        override val blocking: Boolean get() = true
    }

    data object NotTrusted : MultirepoProblem

    data class ListingFailed(val alias: String, val detail: String?) : MultirepoProblem

    data class GroupWorktreeMissing(val branch: String, val alias: String, val path: String) : MultirepoProblem
}

data class InstallationCheck(val state: CheckState, val finding: InstallationFinding)

data class InstallationReport(val checks: List<InstallationCheck>) {
    val failed: Boolean get() = checks.any { it.state == CheckState.FAILED }
}

sealed interface InstallationFinding {
    val id: String

    data class Git(val location: String?, val version: String?, val minimum: String, val detail: String? = null) : InstallationFinding {
        override val id: String get() = "git"
    }

    data class PathSource(val source: HostPathSource, val shell: String?) : InstallationFinding {
        override val id: String get() = "path"
    }

    data class ShogunCommand(
        val state: CommandLineToolState,
        val launcher: String?,
        val folder: String?,
        val restartNeeded: Boolean,
    ) : InstallationFinding {
        override val id: String get() = "shogun"
    }

    data class Hooks(
        val settingsPath: String,
        val state: HooksInstallState?,
        val events: List<String> = emptyList(),
        val shogun: CommandLineToolState = CommandLineToolState.AVAILABLE,
        val invalidDetail: String? = null,
    ) : InstallationFinding {
        override val id: String get() = "claude-hooks"
    }

    data class AgentTool(val tool: AgentToolId, val location: String?) : InstallationFinding {
        override val id: String get() = tool.id
    }

    data class ProjectFiles(
        val project: String,
        val basePath: String,
        val baseMissing: Boolean,
        val checked: Int,
        val unavailable: List<UnavailableLocalFile>,
        val detail: String? = null,
    ) : InstallationFinding {
        override val id: String get() = "project:$project"
    }

    data class ProviderTool(
        val project: String,
        val tool: ProviderToolId,
        val location: String?,
        val problem: ProviderToolProblem? = null,
        val installed: Int? = null,
        val version: String? = null,
        val provisioned: Boolean = false,
        val java: Int? = null,
    ) : InstallationFinding {
        override val id: String get() = "provider:$project:${tool.id}"
    }

    data class MultirepoCheck(
        val folder: String,
        val name: String,
        val repositories: List<String>,
        val groups: Int,
        val problems: List<MultirepoProblem>,
    ) : InstallationFinding {
        override val id: String get() = "multirepo:$name"
    }
}
