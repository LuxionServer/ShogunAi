package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class DockLaunchedChildProcessTest {

    private val root = createTempDirectory("dock-launched-child-process").toRealPath()
    private val home = root.resolve("home").createDirectories()
    private val userBin = root.resolve("tools/bin").createDirectories()
    private val dockVariables = mapOf("PATH" to "/usr/bin:/bin:/usr/sbin:/sbin", "HOME" to home.toString())

    private fun executable(path: Path, script: String): Path {
        path.writeText("#!/bin/sh\n$script\n")
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        return path
    }

    @Test
    fun `a Herdr started by the app opened from the Dock finds a shogun only the login shell's PATH has`() {
        if (HostOs.current() == HostOs.WINDOWS) return
        val shogun = executable(userBin.resolve("shogun"), "exit 0")
        executable(
            userBin.resolve("herdr"),
            """printf '{"result":{"workspace":{"workspace_id":"%s"},"root_pane":{"pane_id":"p1"}}}' "${'$'}(command -v shogun)"""",
        )
        val loginShell = executable(
            root.resolve("login-shell"),
            """printf '\n__SHOGUNAI_PATH__%s__SHOGUNAI_PATH__\n' "$userBin:/usr/bin:/bin"""",
        )
        val environment = SystemHostEnvironment(
            HostOs.current(),
            dockVariables + ("SHELL" to loginShell.toString()),
            home.toString(),
            ProcessBuilderShellCommandExecutor(path = null),
        )
        val executor = ProcessBuilderShellCommandExecutor(path = { environment.path.folders })
        val herdr = CliHerdrClient(executor, ExecutableLocator(environment)::find)

        val opening = runBlocking { herdr.openWorktree("/projects/main-repo", "/projects/worktrees/TASK-1", "TASK-1") }

        assertEquals(shogun.toString(), opening.workspaceId)
    }
}
