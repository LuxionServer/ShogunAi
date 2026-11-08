package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import app.luxion.shogunai.domain.model.SharedVariablesDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigureMultirepoUseCaseTest {

    private val world = MultirepoTestWorld()
    private val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git, newId = { "new-id" })
    private val empty = Multirepo(world.folder, MultirepoDeclaration(worktreesRoot = "../product-worktrees"))
    private val asked = mutableListOf<Triple<String, String?, String>>()
    private fun answering(answer: Boolean) = WorktreesFolderDecision { project, current, proposed ->
        asked += Triple(project, current, proposed)
        answer
    }

    private fun reviewed() = world.resolver.build(world.folder, world.source.declarations.getValue(world.folder))

    @Test
    fun `init writes an empty declaration with the default worktrees root and remembers it`() = runTest {
        world.source.declarations.clear()
        world.source.known.clear()

        val multirepo = configure.init(world.folder, null).getOrThrow()

        assertEquals(MultirepoDeclaration(worktreesRoot = "../product-worktrees"), world.source.declarations[world.folder])
        assertEquals(listOf(world.folder), world.source.known)
        assertEquals(world.worktreesRoot, multirepo.worktreesRoot)
    }

    @Test
    fun `init refuses a folder inside a repository and an existing multirepo`() = runTest {
        assertIs<WorktreeError.InvalidMultirepo>(configure.init("${world.appBase}/docs", null).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.init(world.folder, null).exceptionOrNull())
    }

    @Test
    fun `init accepts the top level of a workspace repository`() = runTest {
        world.source.declarations.clear()
        world.git.repository(world.folder)

        assertTrue(configure.init(world.folder, "/home/dev/projects/elsewhere").isSuccess)
        assertEquals("../elsewhere", world.source.declarations[world.folder]?.worktreesRoot)
    }

    @Test
    fun `a registered project enters unchanged when the proposal is declined`() = runTest {
        val before = world.appProject.copy(config = world.appProject.config.copy(worktreesRoot = "/home/dev/projects/product-app-worktrees", mainBranch = "develop", secretFiles = listOf("local.properties")))
        world.projects.save(before)

        val added = configure.addRepository(empty, "app", world.appBase, answering(false)).getOrThrow()

        assertEquals(before, world.projects.list().first { it.id == "app" })
        assertEquals(WorktreesFolderProposal.DECLINED, added.worktreesFolder)
        assertFalse(added.registered)
        assertEquals(listOf(Triple<String, String?, String>("product-app", "/home/dev/projects/product-app-worktrees", "${world.worktreesRoot}/app")), asked.toList())
        assertEquals(mapOf("app" to "product-app"), world.source.declarations[world.folder]?.repos)
    }

    @Test
    fun `accepting the proposal only changes the project's worktrees folder`() = runTest {
        world.projects.save(world.appProject.copy(config = world.appProject.config.copy(worktreesRoot = "/home/dev/old")))

        configure.addRepository(empty, "app", world.appBase, answering(true)).getOrThrow()

        assertEquals(world.appProject.config.copy(worktreesRoot = "${world.worktreesRoot}/app"), world.projects.list().first { it.id == "app" }.config)
    }

    @Test
    fun `a folder that already matches isn't asked about`() = runTest {
        val added = configure.addRepository(empty, "app", world.appBase, answering(true)).getOrThrow()

        assertEquals(WorktreesFolderProposal.ALREADY_MATCHES, added.worktreesFolder)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a repository without a project is registered with the app's defaults or the proposal`() = runTest {
        world.projects.delete("api")

        val declined = configure.addRepository(empty, "api", world.apiBase, answering(false)).getOrThrow()

        assertTrue(declined.registered)
        assertEquals("product-api", declined.project.name)
        assertEquals("${world.folder}/product-api-worktrees", declined.project.config.worktreesRoot)
        assertEquals("new-id", declined.project.id)

        world.projects.delete("new-id")
        val accepted = configure.addRepository(empty, "api2", world.apiBase, answering(true)).getOrThrow()
        assertEquals("${world.worktreesRoot}/api2", accepted.project.config.worktreesRoot)
    }

    @Test
    fun `an outside repository is stored with an absolute path`() = runTest {
        world.git.repository("/src/backend")

        configure.addRepository(empty, "backend", "/src/backend", answering(false)).getOrThrow()

        assertEquals("/src/backend", world.source.declarations[world.folder]?.repos?.get("backend"))
    }

    @Test
    fun `invalid additions change nothing`() = runTest {
        val existing = Multirepo(world.folder, world.declaration)
        val before = world.source.declarations[world.folder]

        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(existing, "app", world.appBase, answering(false)).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "App", world.appBase, answering(false)).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "x", "/home/dev/not-a-repo", answering(false)).exceptionOrNull())
        assertEquals(before, world.source.declarations[world.folder])
    }

    @Test
    fun `a repository of another multirepo is refused`() = runTest {
        world.source.declarations["/home/dev/other"] = MultirepoDeclaration(mapOf("app" to world.appBase), "../w")
        world.source.known += "/home/dev/other"

        val error = assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "app", world.appBase, answering(false)).exceptionOrNull())

        assertTrue(error.detail.contains("/home/dev/other"))
    }

    @Test
    fun `removing a repository drops it from the defaults and keeps its project`() = runTest {
        val updated = configure.removeRepository(world.multirepo, "api").getOrThrow()

        assertEquals(listOf("app", "docs"), updated.aliases)
        assertEquals(listOf("app"), updated.declaration.defaultRepos)
        assertTrue(world.projects.list().any { it.id == "api" })
    }

    @Test
    fun `renaming a repository keeps its path and its place in the defaults`() = runTest {
        val path = world.multirepo.declaration.repos.getValue("api")

        val renamed = configure.renameRepository(world.multirepo, "api", "backend").getOrThrow()

        assertEquals(listOf("app", "backend", "docs"), renamed.aliases)
        assertEquals(path, renamed.declaration.repos.getValue("backend"))
        assertEquals(listOf("app", "backend"), renamed.declaration.defaultRepos)
        assertEquals(renamed.declaration, world.source.declarations.getValue(world.folder))
        assertIs<WorktreeError.InvalidMultirepo>(configure.renameRepository(renamed, "backend", "app").exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.renameRepository(renamed, "backend", "Back End").exceptionOrNull())
        assertIs<UnknownMultirepoAlias>(configure.renameRepository(renamed, "api", "web").exceptionOrNull())
    }

    @Test
    fun `defaults and agent are validated and saved`() = runTest {
        assertIs<UnknownMultirepoAlias>(configure.setDefaults(world.multirepo, listOf("app", "web")).exceptionOrNull())

        configure.setDefaults(world.multirepo, listOf("docs")).getOrThrow()
        val withAgent = configure.setAgent(Multirepo(world.folder, world.source.declarations.getValue(world.folder)), AgentLaunchConfig("Codex", "codex --full-auto")).getOrThrow()

        assertEquals(listOf("docs"), withAgent.declaration.defaultRepos)
        assertEquals("codex --full-auto", withAgent.agent.resolvedCommand())
        assertEquals("Codex", withAgent.agent.displayName)
        assertNull(world.source.declarations.getValue(world.folder).agent)
        assertEquals(AgentLaunchConfig("Codex", "codex --full-auto"), world.source.agents[world.folder])
    }

    @Test
    fun `the worktrees root is stored relative to the multirepo folder`() = runTest {
        val moved = configure.setWorktreesRoot(world.multirepo, "${world.folder}/../elsewhere").getOrThrow()

        assertEquals("../elsewhere", moved.declaration.worktreesRoot)
        assertEquals("../elsewhere", world.source.declarations[world.folder]?.worktreesRoot)
    }

    @Test
    fun `delete forgets the multirepo and leaves the projects`() = runTest {
        configure.delete(world.multirepo).getOrThrow()

        assertNull(world.source.declarations[world.folder])
        assertTrue(world.source.known.isEmpty())
        assertEquals(3, world.projects.list().size)
    }

    @Test
    fun `init stores a trimmed name and keeps the worktrees root after the folder`() = runTest {
        world.source.declarations.clear()

        val multirepo = configure.init(world.folder, null, "  Product  ").getOrThrow()

        assertEquals(MultirepoDeclaration(worktreesRoot = "../product-worktrees", name = "Product"), world.source.declarations[world.folder])
        assertEquals("Product", multirepo.name)
    }

    @Test
    fun `setName sets, trims and clears the display name`() = runTest {
        val named = configure.setName(world.multirepo, " Product Suite ").getOrThrow()
        assertEquals("Product Suite", named.declaration.name)
        assertEquals("Product Suite", named.name)

        val cleared = configure.setName(named, "   ").getOrThrow()
        assertNull(cleared.declaration.name)
        assertEquals("product", cleared.name)
        assertNull(world.source.declarations.getValue(world.folder).name)
    }

    @Test
    fun `register remembers an existing declaration and registers the repositories without a project`() = runTest {
        world.source.known.clear()
        world.projects.delete("api")

        val registered = configure.register(reviewed(), answering(true)).getOrThrow()

        assertTrue(registered.trustedNow)
        assertEquals(listOf(world.folder), world.source.known)
        assertEquals(listOf("product-api"), registered.registeredProjects.map { it.name })
        assertEquals("${world.worktreesRoot}/api", world.projects.list().first { it.config.baseRepositoryPath == world.apiBase }.config.worktreesRoot)
        assertEquals(world.declaration, world.source.declarations[world.folder])
        assertEquals(listOf(Triple<String, String?, String>("product-api", null, "${world.worktreesRoot}/api")), asked.toList())
    }

    @Test
    fun `register of a known multirepo with every project changes nothing`() = runTest {
        val before = world.projects.list()

        val registered = configure.register(reviewed(), answering(true)).getOrThrow()

        assertFalse(registered.trustedNow)
        assertTrue(registered.registeredProjects.isEmpty())
        assertEquals(before, world.projects.list())
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `register refuses an invalid declaration and a repository of another multirepo`() = runTest {
        world.source.known.clear()
        world.source.declarations["/home/dev/other"] = MultirepoDeclaration(mapOf("app" to world.appBase), "../w")
        world.source.known += "/home/dev/other"

        val claimed = assertIs<WorktreeError.InvalidMultirepo>(configure.register(reviewed(), answering(true)).exceptionOrNull())
        assertTrue(claimed.detail.contains("/home/dev/other"))
        assertEquals(listOf("/home/dev/other"), world.source.known)

        world.source.known.remove("/home/dev/other")
        world.source.declarations[world.folder] = world.declaration.copy(defaultRepos = listOf("web"))
        assertIs<WorktreeError.InvalidMultirepo>(configure.register(reviewed(), answering(true)).exceptionOrNull())
        assertTrue(world.source.known.isEmpty())
    }

    @Test
    fun `register approves the reviewed declaration and refuses one changed since the review`() = runTest {
        world.source.approveAll = false
        world.source.known.clear()
        val review = reviewed()
        assertFalse(review.approved)

        val registered = configure.register(review, answering(true)).getOrThrow()

        assertTrue(registered.trustedNow)
        assertTrue(registered.multirepo.approved)
        assertTrue(reviewed().approved)

        world.source.declarations[world.folder] = world.declaration.copy(worktreesRoot = "../elsewhere")
        assertIs<WorktreeError.MultirepoNeedsReview>(configure.register(registered.multirepo, answering(true)).exceptionOrNull())
    }

    @Test
    fun `the declared name is part of the approval and an unsafe name is rejected`() = runTest {
        world.source.approveAll = false
        val trusted = configure.trust(reviewed()).getOrThrow()

        val named = configure.setName(trusted, "Product").getOrThrow()
        assertTrue(named.approved)

        world.source.declarations[world.folder] = world.source.declarations.getValue(world.folder).copy(name = "Other")
        assertFalse(reviewed().approved)

        world.source.declarations[world.folder] = named.declaration
        assertIs<WorktreeError.InvalidMultirepo>(configure.setName(named, "Product‮evil").exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.setName(named, "x".repeat(81)).exceptionOrNull())
        assertEquals("Product", world.source.declarations.getValue(world.folder).name)
    }

    @Test
    fun `writes keep the multirepo approved and drop a declared agent`() = runTest {
        world.source.approveAll = false
        val declared = world.declaration.copy(agent = AgentLaunchConfig(agentCommand = "touch /tmp/marker; claude"))
        world.source.declarations[world.folder] = declared
        val trusted = configure.trust(world.resolver.build(world.folder, declared)).getOrThrow()
        assertTrue(trusted.approved)

        val updated = configure.setDefaults(trusted, listOf("docs")).getOrThrow()

        assertTrue(updated.approved)
        assertNull(world.source.declarations.getValue(world.folder).agent)
        assertTrue(world.resolver.build(world.folder, world.source.declarations.getValue(world.folder)).approved)
    }

    @Test
    fun `writes keep the shared variables and the approval`() = runTest {
        world.source.approveAll = false
        val env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to "http://localhost:${'$'}{port:api.http}")))
        val declared = world.declaration.copy(env = env)
        world.source.declarations[world.folder] = declared
        val trusted = configure.trust(world.resolver.build(world.folder, declared)).getOrThrow()

        val updated = configure.removeRepository(trusted, "docs").getOrThrow()

        assertTrue(updated.approved)
        assertEquals(env, world.source.declarations.getValue(world.folder).env)
        assertTrue(world.resolver.build(world.folder, world.source.declarations.getValue(world.folder)).approved)
    }

    @Test
    fun `init approves the new multirepo`() = runTest {
        world.source.approveAll = false
        world.source.declarations.clear()
        world.source.known.clear()

        val multirepo = configure.init(world.folder, null).getOrThrow()

        assertTrue(multirepo.approved)
        assertTrue(world.resolver.build(world.folder, world.source.declarations.getValue(world.folder)).approved)
    }

    @Test
    fun `a declaration changed outside ShogunAi refuses writes and changes nothing`() = runTest {
        world.source.approveAll = false
        world.source.approve(world.folder, world.resolver.build(world.folder, world.declaration).approvalSubject)
        val tampered = world.declaration.copy(worktreesRoot = "/tmp/elsewhere")
        world.source.declarations[world.folder] = tampered
        val stale = world.resolver.build(world.folder, world.declaration)

        listOf(
            configure.setDefaults(stale, listOf("docs")).exceptionOrNull(),
            configure.removeRepository(stale, "docs").exceptionOrNull(),
            configure.setWorktreesRoot(stale, "/tmp/w").exceptionOrNull(),
            configure.setAgent(stale, AgentLaunchConfig(agentCommand = "codex")).exceptionOrNull(),
            configure.addRepository(stale, "web", world.docsBase, answering(true)).exceptionOrNull(),
        ).forEach { assertIs<WorktreeError.MultirepoNeedsReview>(it) }
        assertEquals(tampered, world.source.declarations.getValue(world.folder))
        assertTrue(world.source.agents.isEmpty())
    }

    @Test
    fun `trusting approves only the declaration that was reviewed`() = runTest {
        world.source.approveAll = false
        val reviewed = world.resolver.build(world.folder, world.declaration)
        world.source.declarations[world.folder] = world.declaration.copy(worktreesRoot = "/tmp/elsewhere")

        assertIs<WorktreeError.MultirepoNeedsReview>(configure.trust(reviewed).exceptionOrNull())
        assertFalse(world.resolver.build(world.folder, world.source.declarations.getValue(world.folder)).approved)
    }

    @Test
    fun `trusting registers an unknown declaration and revoking forgets it but keeps the file`() = runTest {
        world.source.approveAll = false
        world.source.known.clear()

        val trusted = configure.trust(world.resolver.build(world.folder, world.declaration)).getOrThrow()

        assertTrue(trusted.approved)
        assertEquals(listOf(world.folder), world.source.known)
        assertEquals(world.folder, world.resolver(world.folder).getOrThrow()!!.multirepo.folder)

        configure.revoke(trusted).getOrThrow()

        assertTrue(world.source.known.isEmpty())
        assertEquals(world.declaration, world.source.declarations[world.folder])
        assertNull(world.resolver(world.folder).getOrThrow())
    }

    private val apiUrl = "http://localhost:${'$'}{port:api.http}"

    private fun stored() = world.source.declarations.getValue(world.folder)

    private fun withEnv(byAlias: Map<String, Map<String, String>>): Multirepo {
        world.source.declarations[world.folder] = world.declaration.copy(env = SharedVariablesDeclaration(byAlias))
        return world.resolver.build(world.folder, stored())
    }

    @Test
    fun `setting a shared variable adds or replaces it and keeps the multirepo approved`() = runTest {
        world.source.approveAll = false
        val trusted = configure.trust(world.resolver.build(world.folder, world.declaration)).getOrThrow()

        val added = configure.setSharedVariable(trusted, "app", "API_URL", apiUrl).getOrThrow()
        val replaced = configure.setSharedVariable(added, "app", "API_URL", "http://127.0.0.1:${'$'}{port:api.http}").getOrThrow()

        assertEquals(mapOf("app" to mapOf("API_URL" to "http://127.0.0.1:${'$'}{port:api.http}")), stored().env?.byAlias)
        assertEquals(world.declaration.copy(env = null), stored().copy(env = null))
        assertTrue(replaced.approved)
        assertTrue(world.resolver.build(world.folder, stored()).approved)
    }

    @Test
    fun `an invalid shared variable or an unknown alias changes nothing`() = runTest {
        val ownPort = configure.setSharedVariable(world.multirepo, "app", "DEBUG_PORT", "${'$'}{port}").exceptionOrNull()

        assertIs<WorktreeError.InvalidMultirepo>(ownPort)
        assertTrue(ownPort.detail.contains("DEBUG_PORT"))
        assertIs<WorktreeError.InvalidMultirepo>(configure.setSharedVariable(world.multirepo, "app", "NODE_OPTIONS", "x").exceptionOrNull())
        assertIs<UnknownMultirepoAlias>(configure.setSharedVariable(world.multirepo, "web", "API_URL", "x").exceptionOrNull())
        assertIs<UnknownMultirepoAlias>(configure.setSharedVariables(world.multirepo, mapOf("web" to mapOf("A" to "x"))).exceptionOrNull())
        assertEquals(world.declaration, stored())
    }

    @Test
    fun `unsetting the last shared variable removes env and an undeclared one is refused`() = runTest {
        val declared = withEnv(mapOf("app" to mapOf("API_URL" to apiUrl)))

        assertIs<WorktreeError.InvalidMultirepo>(configure.unsetSharedVariable(declared, "app", "MISSING").exceptionOrNull())
        assertEquals(apiUrl, stored().env?.byAlias?.get("app")?.get("API_URL"))

        val unset = configure.unsetSharedVariable(declared, "app", "API_URL").getOrThrow()

        assertNull(stored().env)
        assertTrue(unset.approved)
    }

    @Test
    fun `setting every shared variable replaces the block`() = runTest {
        val declared = withEnv(mapOf("app" to mapOf("API_URL" to apiUrl)))

        configure.setSharedVariables(declared, mapOf("docs" to mapOf("SITE" to "${'$'}{worktree}"), "app" to emptyMap())).getOrThrow()
        assertEquals(mapOf("docs" to mapOf("SITE" to "${'$'}{worktree}")), stored().env?.byAlias)

        configure.setSharedVariables(world.resolver.build(world.folder, stored()), emptyMap()).getOrThrow()
        assertNull(stored().env)
    }

    @Test
    fun `shared variables are refused in an unapproved multirepo`() = runTest {
        world.source.approveAll = false
        world.source.approve(world.folder, world.resolver.build(world.folder, world.declaration).approvalSubject)
        val tampered = world.declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to apiUrl))))
        world.source.declarations[world.folder] = tampered
        val stale = world.resolver.build(world.folder, world.declaration)

        assertIs<WorktreeError.MultirepoNeedsReview>(configure.setSharedVariable(stale, "app", "API_URL", "x").exceptionOrNull())
        assertIs<WorktreeError.MultirepoNeedsReview>(configure.setSharedVariables(stale, emptyMap()).exceptionOrNull())
        assertEquals(tampered, stored())
    }

    @Test
    fun `an env ShogunAi can't represent refuses every write`() = runTest {
        val broken = world.declaration.copy(env = SharedVariablesDeclaration(problems = listOf("env.app.RETRIES must be a string")))
        world.source.declarations[world.folder] = broken
        val multirepo = world.resolver.build(world.folder, broken)

        listOf(
            configure.setName(multirepo, "Product").exceptionOrNull(),
            configure.setSharedVariable(multirepo, "app", "API_URL", "x").exceptionOrNull(),
            configure.addRepository(multirepo, "web", world.docsBase, answering(true)).exceptionOrNull(),
        ).forEach { error ->
            assertIs<WorktreeError.InvalidMultirepo>(error)
            assertTrue(error.detail.contains("env.app.RETRIES"))
        }
        assertEquals(broken, stored())
    }

    @Test
    fun `renaming a repository carries its shared variables and the references to it`() = runTest {
        val declared = withEnv(mapOf("app" to mapOf("API_URL" to apiUrl), "api" to mapOf("NAME" to "${'$'}{worktree}")))

        val renamed = configure.renameRepository(declared, "api", "backend").getOrThrow()

        assertEquals(
            mapOf("app" to mapOf("API_URL" to "http://localhost:${'$'}{port:backend.http}"), "backend" to mapOf("NAME" to "${'$'}{worktree}")),
            stored().env?.byAlias,
        )
        assertTrue(renamed.approved)
        world.resolver.validate(renamed)
    }

    @Test
    fun `removing a referenced repository is refused and an unreferenced one drops its variables`() = runTest {
        val declared = withEnv(mapOf("app" to mapOf("API_URL" to apiUrl), "docs" to mapOf("SITE" to "${'$'}{worktree}")))

        val refused = configure.removeRepository(declared, "api").exceptionOrNull()
        assertIs<WorktreeError.InvalidMultirepo>(refused)
        assertTrue(refused.detail.contains("app.API_URL"))
        assertEquals(listOf("app", "api", "docs"), stored().repos.keys.toList())

        configure.removeRepository(declared, "docs").getOrThrow()
        assertEquals(mapOf("app" to mapOf("API_URL" to apiUrl)), stored().env?.byAlias)
    }
    @Test
    fun `a multirepo whose declaration is gone can't be registered, trusted or changed`() = runTest {
        val reviewed = reviewed()
        world.source.declarations.clear()

        assertIs<WorktreeError.MultirepoNotFound>(configure.register(reviewed, answering(false)).exceptionOrNull())
        assertIs<WorktreeError.MultirepoNotFound>(configure.trust(reviewed).exceptionOrNull())
        assertIs<WorktreeError.MultirepoNotFound>(configure.removeRepository(reviewed, "api").exceptionOrNull())
    }

    @Test
    fun `a folder inside a repository or a repository already declared can't be added`() = runTest {
        val inside = configure.addRepository(world.multirepo, "sub", "${world.appBase}/module", answering(false)).exceptionOrNull()
        val declared = configure.addRepository(world.multirepo, "again", world.appBase, answering(false)).exceptionOrNull()

        assertTrue(assertIs<WorktreeError.InvalidMultirepo>(inside).detail.contains("isn't the main worktree"))
        assertTrue(assertIs<WorktreeError.InvalidMultirepo>(declared).detail.contains("already declared as 'app'"))
    }

    @Test
    fun `removing or renaming an unknown repository is refused and renaming to the same alias changes nothing`() = runTest {
        assertIs<UnknownMultirepoAlias>(configure.removeRepository(world.multirepo, "web").exceptionOrNull())
        assertIs<UnknownMultirepoAlias>(configure.renameRepository(world.multirepo, "web", "site").exceptionOrNull())

        assertEquals(world.declaration, configure.renameRepository(world.multirepo, "app", "app").getOrThrow().declaration)
    }

    @Test
    fun `a blank agent command is refused`() = runTest {
        assertIs<IllegalArgumentException>(configure.setAgent(world.multirepo, AgentLaunchConfig(agentCommand = " ")).exceptionOrNull())
    }

    @Test
    fun `unsetting a variable of an unknown repository or of one without variables is refused`() = runTest {
        assertIs<UnknownMultirepoAlias>(configure.unsetSharedVariable(world.multirepo, "web", "PORT").exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.unsetSharedVariable(world.multirepo, "app", "PORT").exceptionOrNull())
    }

    @Test
    fun `a project without a worktrees folder is asked about the proposal with no current folder`() = runTest {
        world.projects.save(world.appProject.copy(config = world.appProject.config.copy(worktreesRoot = "")))

        configure.addRepository(empty, "app", world.appBase, answering(false)).getOrThrow()

        assertEquals(listOf(Triple<String, String?, String>("product-app", null, "${world.worktreesRoot}/app")), asked.toList())
    }

    @Test
    fun `a declined repository at the root gets its worktrees folder at the root`() = runTest {
        world.git.repository("/repo")

        configure.addRepository(empty, "root", "/repo", answering(false)).getOrThrow()

        assertEquals("/repo-worktrees", world.projects.list().first { it.config.baseRepositoryPath == "/repo" }.config.worktreesRoot)
    }

    @Test
    fun `an unknown alias lists the repositories or says there are none`() {
        assertTrue(UnknownMultirepoAlias("web", listOf("app", "api")).message.orEmpty().endsWith("its repositories are app, api"))
        assertTrue(UnknownMultirepoAlias("web", emptyList()).message.orEmpty().endsWith("its repositories are none"))
    }
}
