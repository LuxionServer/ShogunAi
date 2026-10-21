package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentEntry
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryConfigRules
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.TemplatePart
import app.luxion.shogunai.domain.model.XcodeProvider

data class GradleDraft(
    val daemonMaxHeap: String = "",
    val idleTimeoutMinutes: String = "",
    val kotlinDaemonMaxHeap: String = "",
)

data class EmulatorDraft(val preferredAvd: String = "")

data class IosSimulatorDraft(val device: String = "", val runtime: String = "")

data class XcodeDraft(val compilationCache: Boolean = false)

data class MavenDraft(val mavenMaxHeap: String = "")

data class ComposeDraft(val portVariables: String = "") {
    val names: List<String> get() = portVariables.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

data class EnvironmentRow(val id: Int, val name: String, val template: String)

sealed interface RepositoryConfigField {
    data object GradleDaemonMaxHeap : RepositoryConfigField
    data object GradleIdleTimeout : RepositoryConfigField
    data object GradleKotlinDaemonMaxHeap : RepositoryConfigField
    data object MavenMaxHeap : RepositoryConfigField
    data object ComposePortVariables : RepositoryConfigField
    data class EnvironmentName(val rowId: Int) : RepositoryConfigField
    data class EnvironmentTemplate(val rowId: Int) : RepositoryConfigField
    data object EnvFile : RepositoryConfigField
}

data class RepositoryConfigProblem(val field: RepositoryConfigField, val issue: RepositoryConfigIssue)

data class RepositoryConfigDraft(
    val gradle: GradleDraft? = null,
    val emulator: EmulatorDraft? = null,
    val iosSimulator: IosSimulatorDraft? = null,
    val xcode: XcodeDraft? = null,
    val maven: MavenDraft? = null,
    val dockerCompose: ComposeDraft? = null,
    val environment: List<EnvironmentRow> = emptyList(),
    val envFile: String = "",
) {
    val problems: List<RepositoryConfigProblem>
        get() = buildList {
            gradle?.let { settings ->
                settings.daemonMaxHeap.trim().takeIf { it.isNotEmpty() }?.let {
                    RepositoryConfigRules.memorySizeIssue(it)?.let { issue -> add(RepositoryConfigProblem(RepositoryConfigField.GradleDaemonMaxHeap, issue)) }
                }
                settings.idleTimeoutMinutes.trim().takeIf { it.isNotEmpty() }?.let {
                    RepositoryConfigRules.minutesIssue(it)?.let { issue -> add(RepositoryConfigProblem(RepositoryConfigField.GradleIdleTimeout, issue)) }
                }
                settings.kotlinDaemonMaxHeap.trim().takeIf { it.isNotEmpty() }?.let {
                    RepositoryConfigRules.memorySizeIssue(it)?.let { issue -> add(RepositoryConfigProblem(RepositoryConfigField.GradleKotlinDaemonMaxHeap, issue)) }
                }
            }
            maven?.mavenMaxHeap?.trim()?.takeIf { it.isNotEmpty() }?.let {
                RepositoryConfigRules.memorySizeIssue(it)?.let { issue -> add(RepositoryConfigProblem(RepositoryConfigField.MavenMaxHeap, issue)) }
            }
            dockerCompose?.let { compose ->
                val names = compose.names
                val nameIssue = names.firstNotNullOfOrNull(RepositoryConfigRules::variableNameIssue)
                    ?: RepositoryConfigIssue.DuplicateVariableName.takeIf { names.distinct().size != names.size }
                nameIssue?.let { add(RepositoryConfigProblem(RepositoryConfigField.ComposePortVariables, it)) }
            }
            val seen = mutableSetOf<String>()
            environment.forEach { row ->
                val name = row.name.trim()
                val nameIssue = RepositoryConfigRules.variableNameIssue(name)
                    ?: RepositoryConfigIssue.DuplicateVariableName.takeUnless { seen.add(name) }
                nameIssue?.let { add(RepositoryConfigProblem(RepositoryConfigField.EnvironmentName(row.id), it)) }
                if (nameIssue == null || nameIssue == RepositoryConfigIssue.DuplicateVariableName) {
                    RepositoryConfigRules.templateIssue(name, row.template)
                        ?.let { add(RepositoryConfigProblem(RepositoryConfigField.EnvironmentTemplate(row.id), it)) }
                }
            }
            envFile.trim().takeIf { it.isNotEmpty() }?.let {
                RepositoryConfigRules.envFileIssue(it)?.let { issue -> add(RepositoryConfigProblem(RepositoryConfigField.EnvFile, issue)) }
            }
        }

    val isValid: Boolean get() = problems.isEmpty()

    fun issueFor(field: RepositoryConfigField): RepositoryConfigIssue? = problems.firstOrNull { it.field == field }?.issue

    fun toEdit(addCopyPatterns: List<String> = emptyList()) =
        RepositoryConfigEdit(
            providers = RepositoryProviders(
                gradle = gradle?.toProvider(),
                emulator = emulator?.let { EmulatorProvider(preferredAvd = it.preferredAvd.trimmedOrNull()) },
                iosSimulator = iosSimulator?.let { IosSimulatorProvider(device = it.device.trimmedOrNull(), runtime = it.runtime.trimmedOrNull()) },
                xcode = xcode?.let { XcodeProvider(compilationCache = it.compilationCache) },
                maven = maven?.let { MavenProvider(mavenMaxHeap = it.mavenMaxHeap.trimmedOrNull()) },
                dockerCompose = dockerCompose?.let { DockerComposeProvider(portVariables = it.names) },
            ),
            environment = environment.map { EnvironmentEntry(it.name.trim(), it.template) },
            envFile = envFile.trimmedOrNull(),
            addCopyPatterns = addCopyPatterns,
        )

    companion object {
        fun of(provisioning: RepositoryProvisioning?): RepositoryConfigDraft {
            val providers = provisioning?.providers ?: RepositoryProviders()
            return RepositoryConfigDraft(
                gradle = providers.gradle?.let {
                    GradleDraft(
                        daemonMaxHeap = it.daemonMaxHeap.orEmpty(),
                        idleTimeoutMinutes = it.idleTimeoutMinutes?.toString().orEmpty(),
                        kotlinDaemonMaxHeap = it.kotlinDaemonMaxHeap.orEmpty(),
                    )
                },
                emulator = providers.emulator?.let { EmulatorDraft(it.preferredAvd.orEmpty()) },
                iosSimulator = providers.iosSimulator?.let { IosSimulatorDraft(it.device.orEmpty(), it.runtime.orEmpty()) },
                xcode = providers.xcode?.let { XcodeDraft(it.compilationCache) },
                maven = providers.maven?.let { MavenDraft(it.mavenMaxHeap.orEmpty()) },
                dockerCompose = providers.dockerCompose?.let { ComposeDraft(it.portVariables.joinToString(", ")) },
                environment = provisioning?.environment?.variables.orEmpty().mapIndexed { index, variable ->
                    EnvironmentRow(index, variable.name, variable.template.joinToString("") { it.asTemplateText() })
                },
                envFile = provisioning?.environment?.envFile.orEmpty(),
            )
        }
    }
}

private fun GradleDraft.toProvider() = GradleProvider(
    daemonMaxHeap = daemonMaxHeap.trimmedOrNull(),
    idleTimeoutMinutes = idleTimeoutMinutes.trimmedOrNull()?.let(RepositoryConfigRules::positiveWholeNumber),
    kotlinDaemonMaxHeap = kotlinDaemonMaxHeap.trimmedOrNull(),
)

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }

private fun TemplatePart.asTemplateText(): String = when (this) {
    is TemplatePart.Literal -> text.replace("$", "$$")
    TemplatePart.WorktreeName -> "\${worktree}"
    is TemplatePart.Port -> if (key.startsWith("port:")) "\${$key}" else "\${port}"
}
