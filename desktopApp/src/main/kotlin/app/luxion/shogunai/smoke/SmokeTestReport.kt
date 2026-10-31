package app.luxion.shogunai.smoke

import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.ui.shell.TourStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class SmokeStepStatus { PASSED, FAILED, SKIPPED }

enum class SmokeStepName(val id: String) {
    READ_COMMAND("read-command"),
    INSTALL_COMMAND("install-command"),
    INSTALL_HOOKS("install-hooks"),
    TOUR("tour"),
    ADD_PROJECT("add-project"),
    CREATE_WORKTREE("create-worktree"),
    CHECK_FILES("check-files"),
    SCENARIO("scenario"),
}

data class SmokeStep(val name: SmokeStepName, val status: SmokeStepStatus, val detail: String = "")

data class SmokeTestReport(
    val steps: List<SmokeStep>,
    val commandLineTool: CommandLineToolStatus? = null,
    val hooksState: HooksInstallState? = null,
    val tourReached: TourStep? = null,
    val tourToolStepShown: Boolean = false,
    val worktreePath: String? = null,
    val copiedFiles: List<String> = emptyList(),
) {
    val passed: Boolean get() = steps.none { it.status == SmokeStepStatus.FAILED }

    val exitStatus: Int get() = if (passed) 0 else 1

    fun toJson(): String = prettyJson.encodeToString(
        buildJsonObject {
            put("passed", passed)
            putJsonArray("steps") {
                steps.forEach { step ->
                    addJsonObject {
                        put("name", step.name.id)
                        put("status", step.status.name.lowercase())
                        put("detail", step.detail)
                    }
                }
            }
            val tool = commandLineTool
            if (tool == null) put("commandLineTool", JsonNull) else putJsonObject("commandLineTool") {
                put("state", tool.state.name)
                put("launcher", tool.launcher)
                put("folder", tool.folder)
                put("restartNeeded", tool.restartNeeded)
                put("canInstall", tool.canInstall)
                put("installedByApp", tool.installedByApp)
                put("usableByHooks", tool.usableByHooks)
            }
            putJsonObject("hooks") { put("state", hooksState?.name.let(::JsonPrimitive)) }
            putJsonObject("tour") {
                put("reached", tourReached?.name.let(::JsonPrimitive))
                put("toolStepShown", tourToolStepShown)
            }
            putJsonObject("worktree") {
                put("path", worktreePath)
                putJsonArray("copiedFiles") { copiedFiles.forEach { add(it) } }
            }
        },
    )

    private companion object {
        val prettyJson = Json { prettyPrint = true }
    }
}
