package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BuildWait
import app.luxion.shogunai.domain.model.DEFAULT_DEVICE_WAIT_MINUTES
import app.luxion.shogunai.domain.model.HeavyBuildCommand
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration

internal class BuildTurnCommands(
    private val engine: ShogunEngine,
    private val workingDirectory: String,
    private val out: (String) -> Unit,
    private val err: (String) -> Unit,
    private val readInput: () -> String,
    private val worktreeOf: suspend (String) -> String,
) {

    suspend fun run(command: BuildCommand): Int = when (command.action) {
        BuildAction.RUN -> runBuild(command)
        BuildAction.STATUS -> status(command)
    }

    private suspend fun runBuild(command: BuildCommand): Int {
        val worktree = worktreeOf(workingDirectory)
        val timeout = command.wait ?: Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES)
        val run = engine.runInBuildTurn(worktree, workingDirectory, command.command, buildWait(timeout)).getOrThrow()
        return run.exitCode ?: EXIT_NOT_STARTED.also { err("shogun: couldn't start ${command.command.first()}") }
    }

    private fun buildWait(timeout: Duration): BuildWait {
        var lastLine: String? = null
        return BuildWait(timeout) { progress ->
            val line = buildWaitProgressLine(progress)
            if (line != lastLine) err(line)
            lastLine = line
        }
    }

    private suspend fun status(command: BuildCommand): Int {
        val state = engine.readBuildTurn().getOrThrow()
        if (command.json) out(buildStateJson(state).compact()) else buildStateLines(state).forEach(out)
        return EXIT_OK
    }

    suspend fun hook(): Int {
        try {
            val input = Json.parseToJsonElement(readInput()) as? JsonObject ?: return EXIT_OK
            if (input.text("tool_name") != BASH_TOOL) return EXIT_OK
            val command = (input["tool_input"] as? JsonObject)?.text("command") ?: return EXIT_OK
            val builds = HeavyBuildCommand.unwrappedBuilds(command)
            if (builds.isEmpty() || engine.resolveLimits.buildSlots() == null) return EXIT_OK
            val directory = input.text("cwd") ?: workingDirectory
            if (engine.resolveRepository(directory).isFailure) return EXIT_OK
            out(denial(builds).compact())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return EXIT_OK
        }
        return EXIT_OK
    }

    private fun denial(builds: List<String>): JsonObject = buildJsonObject {
        put(
            "hookSpecificOutput",
            buildJsonObject {
                put("hookEventName", "PreToolUse")
                put("permissionDecision", "deny")
                put("permissionDecisionReason", buildTurnDenial(builds))
            },
        )
    }

    private fun JsonObject.text(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        return value.content.takeIf { value.isString && it.isNotBlank() }
    }

    private companion object {
        const val EXIT_OK = 0
        const val EXIT_NOT_STARTED = 127
        const val BASH_TOOL = "Bash"
    }
}
