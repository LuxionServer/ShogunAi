package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.UnresolvedReason
import app.luxion.shogunai.domain.model.UnresolvedVariable
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.parseSharedEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedEnvironmentResolutionTest {

    private val appBase = "/home/dev/projects/product-app"
    private val apiBase = "/home/dev/projects/product-api"
    private val appTask = "/home/dev/projects/product-worktrees/app/TASK-7"
    private val apiTask = "/home/dev/projects/product-worktrees/api/TASK-7"
    private val appAdmin = "$appBase/.git/worktrees/TASK-7"
    private val apiAdmin = "$apiBase/.git/worktrees/TASK-7"
    private val directories = mapOf(
        appBase to ("$appBase/.git" to "$appBase/.git"),
        appTask to (appAdmin to "$appBase/.git"),
        apiBase to ("$apiBase/.git" to "$apiBase/.git"),
        apiTask to (apiAdmin to "$apiBase/.git"),
    )
    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            val (admin, common) = directories.getValue(workingDirectory!!)
            return FakeShellCommandExecutor.success(command, "$admin\n$common\n")
        }
    }
    private var store = FakeEnvironmentStateStore()
    private val apiProvisioning = provisioning("SERVER_PORT" to "\${port:http}", "DEBUG_PORT" to "\${port}")
    private var appProvisioning = provisioning()
    private var trust = FakeRepositoryTrust(approvesEverything = true)
    private var target: SharedTarget = SharedTarget.Located(apiBase, apiTask)
    private var shared = listOf("API_URL" to "http://localhost:\${port:api.http}")

    private fun provisioning(vararg variables: Pair<String, String>, providers: RepositoryProviders = RepositoryProviders()) =
        RepositoryProvisioning(
            environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            providers = providers,
        )

    private fun source() = SharedEnvironmentSource { repository, _ ->
        if (repository != appBase) {
            null
        } else {
            SharedEnvironment(
                shared.map { (name, value) -> parseSharedEnvironmentVariable(name, value, setOf("app", "api")) },
                mapOf("api" to target),
            )
        }
    }

    private fun resolver() = ResolveWorktreeEnvironmentUseCase(
        git,
        FakeRepositoryProvisioningSource(mapOf(appBase to appProvisioning, apiBase to apiProvisioning)),
        store,
        FakePortProbe(),
        trust = trust,
        sharedEnvironment = source(),
    )

    @Test
    fun `a shared variable gets the port its partner reserves`() = runTest {
        val app = resolver()(appBase, appTask).getOrThrow()
        val api = resolver()(apiBase, apiTask).getOrThrow()

        val serverPort = api.variables.toMap().getValue("SERVER_PORT")
        assertEquals("http://localhost:$serverPort", app.variables.toMap()["API_URL"])
        assertEquals(store.readPorts(apiAdmin)["port:http"].toString(), serverPort)
        assertEquals(setOf("port:http", "DEBUG_PORT"), store.readPorts(apiAdmin).keys)
        assertTrue(store.readPorts(appAdmin).isEmpty())
    }

    @Test
    fun `the worktree name in a shared variable is the receiving worktree's`() = runTest {
        shared = listOf("APP_SUFFIX" to "-\${worktree}")

        assertEquals(listOf("APP_SUFFIX" to "-TASK-7"), resolver()(appBase, appTask).getOrThrow().variables)
    }

    @Test
    fun `reading without reservation reserves nothing and reports the variable`() = runTest {
        val environment = resolver()(appBase, appTask, reserve = false).getOrThrow()

        assertTrue(environment.variables.isEmpty())
        assertEquals(listOf(UnresolvedVariable("API_URL", UnresolvedReason.PortNotReserved("api", "http"))), environment.unresolved)
        assertTrue(store.readPorts(apiAdmin).isEmpty())
    }

    @Test
    fun `the repository's own variable wins over a shared one, and a shared one over a provider's`() = runTest {
        store = FakeEnvironmentStateStore(mapOf(appAdmin to mapOf("emulator" to 5554)))
        appProvisioning = provisioning("API_URL" to "http://localhost:9999", providers = RepositoryProviders(emulator = EmulatorProvider()))
        shared = listOf("API_URL" to "http://localhost:\${port:api.http}", "ANDROID_SERIAL" to "emulator-\${port:api.http}")

        val variables = resolver()(appBase, appTask).getOrThrow().variables

        assertEquals(listOf("http://localhost:9999"), variables.filter { it.first == "API_URL" }.map { it.second })
        assertEquals(listOf("ANDROID_SERIAL"), variables.filter { it.first == "ANDROID_SERIAL" }.map { it.first })
        assertTrue(variables.toMap().getValue("ANDROID_SERIAL").startsWith("emulator-2"))
    }

    @Test
    fun `shared variables don't depend on the receiving repository's approval`() = runTest {
        appProvisioning = provisioning("PORT" to "\${port}")
        trust = FakeRepositoryTrust(approved = mapOf(apiBase to apiProvisioning.approvalSubject))

        val environment = resolver()(appBase, appTask).getOrThrow()

        assertEquals(listOf("API_URL"), environment.variables.map { it.first })
        assertEquals(listOf("PORT"), environment.awaitingApproval)
    }

    @Test
    fun `an unapproved referenced repository leaves the variable out and reserves nothing there`() = runTest {
        trust = FakeRepositoryTrust(approved = mapOf(appBase to appProvisioning.approvalSubject))

        val environment = resolver()(appBase, appTask).getOrThrow()

        assertTrue(environment.variables.isEmpty())
        assertEquals(listOf(UnresolvedVariable("API_URL", UnresolvedReason.RepositoryNotApproved("api", apiBase))), environment.unresolved)
        assertTrue(store.readPorts(apiAdmin).isEmpty())
    }

    @Test
    fun `a key the referenced repository doesn't declare is reported`() = runTest {
        shared = listOf("API_URL" to "http://localhost:\${port:api.admin}", "APP_NAME" to "demo")

        val environment = resolver()(appBase, appTask).getOrThrow()

        assertEquals(listOf("APP_NAME" to "demo"), environment.variables)
        assertEquals(listOf(UnresolvedVariable("API_URL", UnresolvedReason.PortNotDeclared("api", "admin"))), environment.unresolved)
    }

    @Test
    fun `an unreadable worktree is reported without failing the environment`() = runTest {
        target = SharedTarget.Unreadable("not a git repository")

        val environment = resolver()(appBase, appTask).getOrThrow()

        assertEquals(listOf(UnresolvedVariable("API_URL", UnresolvedReason.WorktreeUnreadable("api", "not a git repository"))), environment.unresolved)
    }

    @Test
    fun `an invalid declaration reports the repository's shared variables`() = runTest {
        val reason = UnresolvedReason.InvalidDeclaration("/home/dev/projects/product/.shogunai-multirepo.json", "env: 'web' isn't a declared repository")
        val invalid = ResolveWorktreeEnvironmentUseCase(
            git,
            FakeRepositoryProvisioningSource(mapOf(appBase to provisioning("PORT" to "\${port}"))),
            store,
            FakePortProbe(),
            trust = trust,
            sharedEnvironment = { _, _ -> SharedEnvironment(invalid = reason, invalidNames = listOf("API_URL")) },
        )

        val environment = invalid(appBase, appTask).getOrThrow()

        assertEquals(listOf("PORT"), environment.variables.map { it.first })
        assertEquals(listOf(UnresolvedVariable("API_URL", reason)), environment.unresolved)
    }

    @Test
    fun `the main checkout's port is used when the target is the main checkout`() = runTest {
        target = SharedTarget.Located(apiBase, apiBase)

        val app = resolver()(appBase, appTask).getOrThrow()

        assertEquals("http://localhost:${store.readPorts("$apiBase/.git")["port:http"]}", app.variables.toMap()["API_URL"])
    }

    @Test
    fun `a worktree that fails without a reason is reported unreadable with no detail`() = runTest {
        target = SharedTarget.Located(apiBase, "/home/dev/projects/product-worktrees/api/GONE")
        val silent = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                val (admin, common) = directories[workingDirectory] ?: throw IllegalStateException()
                return FakeShellCommandExecutor.success(command, "$admin\n$common\n")
            }
        }
        val resolver = ResolveWorktreeEnvironmentUseCase(
            silent,
            FakeRepositoryProvisioningSource(mapOf(appBase to appProvisioning, apiBase to apiProvisioning)),
            store,
            FakePortProbe(),
            trust = trust,
            sharedEnvironment = source(),
        )

        val environment = resolver(appBase, appTask).getOrThrow()

        assertEquals(listOf(UnresolvedVariable("API_URL", UnresolvedReason.WorktreeUnreadable("api", ""))), environment.unresolved)
    }
}
