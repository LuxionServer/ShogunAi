package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.gradleBuildFolders
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.JavaSource
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath
import kotlinx.coroutines.CancellationException

class CheckProviderToolsUseCase(
    private val executor: ShellCommandExecutor,
    private val findExecutable: (String) -> String?,
    private val fileManager: FileManager,
    private val androidSdk: AndroidSdk,
    private val simulators: AppleSimulators,
    private val environmentVariable: (String) -> String?,
    private val os: HostOs,
    home: String,
) {

    private val gradleSettings = GradleJavaSettingsReader(fileManager, environmentVariable, home)
    private val javaInstallations = JavaInstallations(fileManager, environmentVariable, os, home)

    suspend operator fun invoke(project: String, repository: String, providers: RepositoryProviders): List<InstallationCheck> {
        val tools = buildList<Pair<ProviderToolId, suspend () -> Located>> {
            if (providers.gradle != null) add(ProviderToolId.GRADLE to { buildTool(gradleBuild(repository), BuildTool.GRADLE) })
            if (providers.maven != null) add(ProviderToolId.MAVEN to { buildTool(repository, BuildTool.MAVEN) })
            if (providers.emulator != null) add(ProviderToolId.ANDROID_SDK to { androidSdk(repository) })
            if (providers.iosSimulator != null) add(ProviderToolId.IOS_SIMULATOR to ::iosSimulators)
            if (providers.xcode != null) add(ProviderToolId.XCODE to ::xcode)
            if (providers.xcode?.cocoaPods == true) add(ProviderToolId.COCOAPODS to { commandOnPath("pod") })
            if (providers.dockerCompose != null) add(ProviderToolId.DOCKER_COMPOSE to ::dockerCompose)
            if (providers.flutter != null) add(ProviderToolId.FLUTTER to { commandOnPath(flutterCommand(fileManager, repository).first()) })
        }
        return tools.flatMap { (tool, locate) ->
            val located = locateGuarded(locate)
            val dependents = located.then?.invoke().orEmpty()
            listOf(check(project, tool, located)) + dependents.map { (next, nextLocated) -> check(project, next, nextLocated) }
        }
    }

    private fun check(project: String, tool: ProviderToolId, located: Located): InstallationCheck {
        val finding = InstallationFinding.ProviderTool(
            project, tool, located.location, located.problem, located.installed, located.version, located.provisioned, located.java,
        )
        return InstallationCheck(if (located.problem == null) CheckState.PASSED else CheckState.WARNING, finding)
    }

    private suspend fun locateGuarded(locate: suspend () -> Located): Located = try {
        locate()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Located(null, ProviderToolProblem.Unusable(error.message))
    }

    fun unreadable(project: String, detail: String?): InstallationCheck = InstallationCheck(
        CheckState.WARNING,
        InstallationFinding.ProviderTool(project, ProviderToolId.PROVIDERS, null, ProviderToolProblem.ProvidersUnreadable(detail)),
    )

    private data class Located(
        val location: String?,
        val problem: ProviderToolProblem? = null,
        val installed: Int? = null,
        val version: String? = null,
        val then: (suspend () -> List<Pair<ProviderToolId, Located>>)? = null,
        val provisioned: Boolean = false,
        val java: Int? = null,
        val runtime: InstalledJava? = null,
    )

    private enum class BuildTool(
        val wrapper: String,
        val command: String,
        val wrapperProperties: String,
        val java: ProviderToolId,
        val distributionVersion: (String?) -> String?,
        val printedVersion: (String) -> String?,
        val minimumJava: (String) -> Int,
        val firstSupporting: (Int) -> String?,
    ) {
        GRADLE(
            "gradlew", "gradle", "gradle/wrapper/gradle-wrapper.properties", ProviderToolId.GRADLE_JAVA,
            ToolVersions::gradleDistribution, ToolVersions::gradlePrinted, JavaCompatibility::minimumJavaForGradle, JavaCompatibility::firstGradleFor,
        ),
        MAVEN(
            "mvnw", "mvn", ".mvn/wrapper/maven-wrapper.properties", ProviderToolId.MAVEN_JAVA,
            ToolVersions::mavenDistribution, ToolVersions::mavenPrinted, JavaCompatibility::minimumJavaForMaven, { null },
        ),
    }

    private fun gradleBuild(repository: String): String {
        val folder = gradleBuildFolders(fileManager, repository).firstOrNull()
        return if (folder.isNullOrEmpty()) repository else joinPath(repository, folder)
    }

    private suspend fun buildTool(repository: String, tool: BuildTool): Located {
        val wrapperPath = joinPath(repository, tool.wrapper)
        val (location, version) = if (fileManager.exists(wrapperPath)) {
            wrapperPath to tool.distributionVersion(fileManager.readProperties(joinPath(repository, tool.wrapperProperties))["distributionUrl"])
        } else {
            val command = findExecutable(tool.command) ?: return Located(null, ProviderToolProblem.NotFound(listOf(wrapperPath), listOf(tool.command)))
            command to printedVersion(command, tool.printedVersion)
        }
        return Located(location, version = version, then = { javaChecks(repository, tool, version) })
    }

    private suspend fun javaChecks(repository: String, tool: BuildTool, version: String?): List<Pair<ProviderToolId, Located>> {
        val client = locateGuarded { java(tool, version) }
        val build = if (tool == BuildTool.GRADLE) gradleBuildChecksGuarded(repository, version, client.runtime) else emptyList()
        return listOf(tool.java to client) + build
    }

    private suspend fun gradleBuildChecksGuarded(repository: String, gradle: String?, client: InstalledJava?): List<Pair<ProviderToolId, Located>> = try {
        gradleBuildChecks(repository, gradle, client)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        listOf(ProviderToolId.GRADLE_DAEMON_JAVA to Located(null, ProviderToolProblem.Unusable(error.message)))
    }

    private suspend fun gradleBuildChecks(repository: String, gradle: String?, client: InstalledJava?): List<Pair<ProviderToolId, Located>> {
        val settings = gradleSettings.read(repository)
        val search by lazy { javaInstallations.search(settings, gradleSettings.gradleUserHome, client) }
        val daemon = daemonJava(settings, gradle) { search }
        val toolchains = toolchains(settings) { search }
        val daemonJava = if (daemon != null) daemon.java?.let { it to daemon.source } else client?.let { it.major to JavaSource.ENVIRONMENT }
        val agp = settings.androidGradlePlugin?.let { agp -> daemonJava?.let { (java, source) -> androidGradlePlugin(agp, java, source) } }
        return listOfNotNull(
            daemon?.let { ProviderToolId.GRADLE_DAEMON_JAVA to it.located },
            toolchains?.let { ProviderToolId.GRADLE_TOOLCHAINS to it },
            agp?.let { ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA to it },
        )
    }

    private data class DaemonJava(val located: Located, val java: Int?, val source: JavaSource)

    private suspend fun daemonJava(settings: GradleJavaSettings, gradle: String?, search: () -> JavaSearch): DaemonJava? {
        val criteria = settings.daemonCriteria?.takeIf { criteria ->
            gradle?.let(ToolVersions::numeric)?.let { CheckInstallationUseCase.isAtLeast(it, DAEMON_CRITERIA_GRADLE) } ?: true
        }
        if (criteria != null) return criteriaJava(criteria, settings, gradle, search)
        val javaHome = settings.javaHome ?: return null
        return propertyJava(javaHome, gradle)
    }

    private fun criteriaJava(criteria: DaemonJvmCriteria, settings: GradleJavaSettings, gradle: String?, search: () -> JavaSearch): DaemonJava {
        val source = JavaSource.DAEMON_CRITERIA
        val version = criteria.version
            ?: return DaemonJava(Located(criteria.path, ProviderToolProblem.DaemonCriteriaUnreadable(criteria.path)), null, source)
        val installed = search().find(version)
        val located = compatibility(BuildTool.GRADLE, gradle, version, source)?.let { Located(installed?.home, it, version = version.toString()) }
            ?: installed?.let { Located(it.home, version = version.toString()) }
            ?: if (criteria.downloadable && settings.autoDownload) {
                Located(null, version = version.toString(), provisioned = true)
            } else {
                Located(null, ProviderToolProblem.JavaNotInstalled(listOf(version), search().searched), version = version.toString())
            }
        return DaemonJava(located, version, source)
    }

    private suspend fun propertyJava(javaHome: GradleJavaHome, gradle: String?): DaemonJava {
        val source = JavaSource.GRADLE_PROPERTY
        val java = javaUnder(javaHome.value).takeIf(fileManager::exists)
            ?: return DaemonJava(Located(null, ProviderToolProblem.GradleJavaHomeInvalid(javaHome.value, javaHome.propertiesFile)), null, source)
        val measured = measure(java)
        val major = measured.runtime?.major ?: return DaemonJava(measured, null, source)
        return DaemonJava(Located(java, compatibility(BuildTool.GRADLE, gradle, major, source), version = major.toString()), major, source)
    }

    private fun toolchains(settings: GradleJavaSettings, search: () -> JavaSearch): Located? {
        if (settings.toolchains.isEmpty()) return null
        val versions = settings.toolchains.joinToString(", ")
        val missing = settings.toolchains.filter { search().find(it) == null }
        return when {
            missing.isEmpty() -> Located(null, version = versions)
            settings.toolchainResolver && settings.autoDownload -> Located(null, version = versions, provisioned = true)
            else -> Located(null, ProviderToolProblem.JavaNotInstalled(missing, search().searched), version = versions)
        }
    }

    private fun androidGradlePlugin(agp: String, java: Int, source: JavaSource): Located {
        val required = JavaCompatibility.minimumJavaForAndroidGradlePlugin(agp)
        val problem = if (java < required) ProviderToolProblem.AndroidGradlePluginNeedsJava(java, required, agp, source) else null
        return Located(null, problem, version = agp, java = java)
    }

    private suspend fun printedVersion(command: String, parse: (String) -> String?): String? = try {
        executor.executeWithin(listOf(command, "--version"), CommandTimeouts.LISTING).takeIf { it.isSuccess }?.let { parse(it.stdout) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun java(tool: BuildTool, toolVersion: String?): Located {
        val javaHome = environmentVariable(JAVA_HOME)?.takeIf { it.isNotBlank() }
        val java = if (javaHome != null) {
            javaUnder(javaHome).takeIf(fileManager::exists) ?: return Located(null, ProviderToolProblem.JavaHomeInvalid(javaHome))
        } else {
            findExecutable("java") ?: return Located(null, ProviderToolProblem.NotFound(listOf(JAVA_HOME), listOf("java")))
        }
        val measured = measure(java)
        val major = measured.runtime?.major ?: return measured
        return measured.copy(problem = compatibility(tool, toolVersion, major, JavaSource.ENVIRONMENT))
    }

    private suspend fun measure(java: String): Located {
        val result = executor.executeWithin(listOf(java, "-version"), CommandTimeouts.LISTING)
        if (!result.isSuccess) return Located(java, ProviderToolProblem.Unusable(output(result)))
        val major = ToolVersions.javaMajor(result.stderr + "\n" + result.stdout) ?: return Located(java, ProviderToolProblem.Unusable(output(result)))
        return Located(java, version = major.toString(), runtime = InstalledJava(java, major))
    }

    private fun javaUnder(javaHome: String): String =
        if (os.isWindows) "${javaHome.trimEnd('\\', '/')}\\bin\\java.exe" else joinPath(javaHome, "bin", "java")

    private fun compatibility(tool: BuildTool, toolVersion: String?, java: Int, source: JavaSource): ProviderToolProblem? {
        val version = toolVersion?.let(ToolVersions::numeric) ?: return null
        val required = tool.minimumJava(version)
        if (java < required) return ProviderToolProblem.JavaTooOld(java, required, toolVersion, source)
        val first = tool.firstSupporting(java) ?: return null
        return if (CheckInstallationUseCase.isAtLeast(version, first)) null else ProviderToolProblem.GradleTooOld(java, toolVersion, first, source)
    }

    private suspend fun dockerCompose(): Located = runs("docker", listOf("compose", "version")) { docker, output ->
        val version = ToolVersions.composePrinted(output)
        val tooOld = version?.takeUnless { CheckInstallationUseCase.isAtLeast(it, ToolVersions.MINIMUM_COMPOSE) }
        Located(
            docker,
            tooOld?.let { ProviderToolProblem.TooOld(it, ToolVersions.MINIMUM_COMPOSE) },
            version = version,
            then = { listOf(ProviderToolId.DOCKER_DAEMON to locateGuarded { dockerDaemon(docker) }) },
        )
    }

    private suspend fun dockerDaemon(docker: String): Located {
        val result = executor.executeWithin(listOf(docker, "info", "--format", "{{.ServerVersion}}"), CommandTimeouts.LISTING)
        if (!result.isSuccess) return Located(null, ProviderToolProblem.DaemonNotRunning(os, output(result)))
        return Located(null, version = result.stdout.trim().ifEmpty { null })
    }

    private suspend fun androidSdk(repository: String): Located {
        val sdk = try {
            androidSdk.locate(repository)
        } catch (error: WorktreeError.AndroidSdkNotFound) {
            return Located(null, ProviderToolProblem.NotFound(error.checked))
        }
        val avds = androidSdk.listAvds(sdk).size
        return Located(sdk.root, if (avds == 0) ProviderToolProblem.NoAndroidVirtualDevice else null, avds)
    }

    private suspend fun iosSimulators(): Located {
        if (!simulators.isSupported()) return Located(null, ProviderToolProblem.NeedsMacOs)
        val runtimes = simulators.runtimes().count { it.platform.equals("iOS", ignoreCase = true) }
        return Located(null, if (runtimes == 0) ProviderToolProblem.NoIosRuntime else null, runtimes)
    }

    private suspend fun xcode(): Located =
        if (simulators.isSupported()) runs("xcodebuild", listOf("-version")) else Located(null, ProviderToolProblem.NeedsMacOs)

    private fun commandOnPath(command: String): Located {
        val location = findExecutable(command)
        return Located(location, if (location == null) ProviderToolProblem.NotFound(emptyList(), listOf(command)) else null)
    }

    private suspend fun runs(
        command: String,
        arguments: List<String>,
        succeeded: suspend (String, String) -> Located = { location, _ -> Located(location) },
    ): Located {
        val location = findExecutable(command) ?: return Located(null, ProviderToolProblem.NotFound(emptyList(), listOf(command)))
        val result = executor.executeWithin(listOf(location) + arguments, CommandTimeouts.LISTING)
        if (result.isSuccess) return succeeded(location, result.stdout)
        return Located(location, ProviderToolProblem.Unusable(output(result)))
    }

    private fun output(result: CommandResult): String =
        result.stderr.ifBlank { result.stdout }.trim().ifEmpty { "exit code ${result.exitCode}" }

    private companion object {
        const val JAVA_HOME = "JAVA_HOME"
        const val DAEMON_CRITERIA_GRADLE = "8.8"
    }
}
