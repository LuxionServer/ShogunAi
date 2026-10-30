package app.luxion.shogunai.smoke

import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.model.BranchType
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths

const val SMOKE_TEST_REPORT_VARIABLE = "SHOGUNAI_SMOKE_TEST_REPORT"
const val SMOKE_TEST_LOCAL_FILES_VARIABLE = "SHOGUNAI_SMOKE_TEST_LOCAL_FILES"

fun smokeTestReportPath(arguments: List<String>, environment: Map<String, String>): String? =
    environment[SMOKE_TEST_REPORT_VARIABLE]?.takeIf { arguments.isEmpty() && it.isNotBlank() }

fun smokeTestLocalFiles(environment: Map<String, String>): List<String> =
    environment[SMOKE_TEST_LOCAL_FILES_VARIABLE].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

fun runSmokeTest(reportPath: String, environment: Map<String, String>): Int {
    val report = runCatching {
        runBlocking { smokeTestScenario(AppContainer()).run(environment[SmokeTestScenario.REPOSITORY_VARIABLE], smokeTestLocalFiles(environment)) }
    }.getOrElse { error ->
        SmokeTestReport(listOf(SmokeStep(SmokeStepName.SCENARIO, SmokeStepStatus.FAILED, error.stackTraceToString())))
    }
    writeSmokeTestReport(reportPath, report)
    return report.exitStatus
}

fun writeSmokeTestReport(reportPath: String, report: SmokeTestReport) {
    val path = Paths.get(reportPath).toAbsolutePath()
    path.parent?.let(Files::createDirectories)
    Files.writeString(path, report.toJson())
}

private fun smokeTestScenario(container: AppContainer) = SmokeTestScenario(
    hooks = container.claudeHooks,
    tour = container.tour,
    projectForm = { container.projectConfigViewModel(existingProject = null) },
    declaredCopies = { repository -> container.repositoryProvisioningSource.read(repository).copyPatterns },
    createWorktree = { config, taskId -> container.worktreeUseCases(config).create(taskId, BranchType.FEATURE) },
)
