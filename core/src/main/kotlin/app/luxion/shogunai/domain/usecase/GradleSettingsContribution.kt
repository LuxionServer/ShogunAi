package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.GradleProvider

const val GRADLE_OPTS = "GRADLE_OPTS"

private const val GRADLE_PROPERTIES = "gradle.properties"
private const val JVM_ARGS_PROPERTY = "org.gradle.jvmargs"
private const val MILLISECONDS_PER_MINUTE = 60_000L
private val MAX_HEAP_ARGUMENT = Regex("-Xmx\\S*")

fun gradleDaemonArguments(effective: String?, maxHeap: String): String {
    val heap = "-Xmx$maxHeap"
    if (effective == null || '"' in effective) return heap
    val others = effective.split(Regex("\\s+")).filter { it.isNotEmpty() && !MAX_HEAP_ARGUMENT.matches(it) }
    return (listOf(heap) + others).joinToString(" ")
}

fun gradleOpts(settings: GradleProvider, effectiveJvmArgs: String?): String =
    listOfNotNull(
        settings.daemonMaxHeap?.let { "-D$JVM_ARGS_PROPERTY=\"${gradleDaemonArguments(effectiveJvmArgs, it)}\"" },
        settings.idleTimeoutMinutes?.let { "-Dorg.gradle.daemon.idletimeout=${it * MILLISECONDS_PER_MINUTE}" },
        settings.kotlinDaemonMaxHeap?.let { "-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx$it" },
    ).joinToString(" ")

fun effectiveGradleJvmArgs(fileManager: FileManager, gradleUserHome: String, repositoryPath: String): String? =
    listOf(gradleUserHome, repositoryPath).firstNotNullOfOrNull { directory ->
        fileManager.readProperties("${directory.trimEnd('/', '\\')}/$GRADLE_PROPERTIES")[JVM_ARGS_PROPERTY]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

fun gradleSettingsContribution(settings: GradleProvider, effectiveJvmArgs: String?) = EnvironmentContribution(
    variables = { listOf(GRADLE_OPTS to gradleOpts(settings, effectiveJvmArgs)) },
)
