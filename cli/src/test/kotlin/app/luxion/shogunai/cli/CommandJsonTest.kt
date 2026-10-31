package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentSessionRef
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.MavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.usecase.GroupState
import app.luxion.shogunai.domain.usecase.GroupWorktree
import app.luxion.shogunai.domain.usecase.GroupWorktreeState
import app.luxion.shogunai.domain.usecase.MultirepoState
import app.luxion.shogunai.domain.usecase.MultirepoWorktrees
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandJsonTest {

    private fun JsonObject.text(key: String): String? = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.content

    private fun JsonObject.objects(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

    @Test
    fun `status json keeps the status problem and leaves a missing upstream and last commit null`() {
        val status = WorktreeStatus(uncommittedChanges = 0, upstream = null, mainBranch = Divergence(1, 0), lastCommitAt = null)
        val entry = WorktreeEntry(Worktree("/w", "feature/TASK-1"), status, origin = null, statusProblem = "git failed")

        val worktree = statusJson("/repo", "main", listOf(entry)).objects("worktrees").single()

        assertEquals("git failed", worktree.text("statusProblem"))
        assertEquals(JsonNull, worktree.getValue("status").jsonObject.getValue("upstream"))
        assertEquals(null, worktree.getValue("status").jsonObject.text("lastCommitAt"))
    }

    @Test
    fun `status json shows the upstream and the last commit when there are`() {
        val status = WorktreeStatus(uncommittedChanges = 0, upstream = Divergence(2, 1), mainBranch = null, lastCommitAt = Instant.EPOCH)

        val json = statusJson("/repo", null, listOf(WorktreeEntry(Worktree("/w", null), status, origin = null))).objects("worktrees").single().getValue("status").jsonObject

        assertEquals("2", json.getValue("upstream").jsonObject.text("ahead"))
        assertEquals(Instant.EPOCH.toString(), json.text("lastCommitAt"))
    }

    @Test
    fun `an agent's herdr state is only set when Herdr reports it`() {
        val fromHerdr = agentJson(RunningAgent("claude", state = AgentState.WORKING, stateSource = AgentStateSource.HERDR))
        val fromOrca = agentJson(RunningAgent("claude", state = AgentState.WORKING, stateSource = AgentStateSource.ORCA))

        assertEquals("working", fromHerdr.text("herdrState"))
        assertEquals("working", fromOrca.text("state"))
        assertEquals(null, fromOrca.text("herdrState"))
    }

    @Test
    fun `a pub get that exited names its error lines when it has any`() {
        assertEquals("exited with code 1", flutterPubGetReason(FlutterPubGetFailure.Exited(1, emptyList())))
        assertEquals("exited with code 1: a | b", flutterPubGetReason(FlutterPubGetFailure.Exited(1, listOf("a", "b"))))
    }

    @Test
    fun `provision json lists the files copied instead of linked`() {
        val json = provisionJson(
            "/w",
            listOf(ProvisionedFile("a", ProvisionMode.COPIED), ProvisionedFile("b", ProvisionMode.COPIED_INSTEAD_OF_LINKING)),
            emptyList(),
            emptyList(),
            null,
        )

        assertEquals(listOf("b"), json.getValue("copiedInsteadOfLinking").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `clean json names why each candidate goes and its compose leftovers`() {
        val json = cleanJson(
            CleanJson(
                dryRun = true,
                candidates = listOf(CleanCandidateJson("/a", "a", folderMissing = true), CleanCandidateJson("/b", "b", folderMissing = false)),
                composeProjectsDown = listOf("p"),
                composeResources = ComposeLeftoversReport(),
            ),
        )

        assertEquals(listOf("folder-missing", "merged"), json.objects("candidates").map { it.text("reason") })
        val providers = json.getValue("providers").jsonObject
        assertTrue("composeProjectsDown" in providers && "composeResources" in providers)
        assertFalse("composeProjectsDown" in cleanJson(CleanJson(dryRun = true)).getValue("providers").jsonObject)
    }

    private fun gradleDaemon(state: DaemonState, owner: DaemonOwner?, holder: HoldingBuild? = null) =
        GradleDaemon(DaemonProcess(1, DaemonKind.GRADLE, "9.0", 10, null, state, holder), owner)

    @Test
    fun `gradle json names each daemon's owner only when attributed`() {
        val daemons = listOf(gradleDaemon(DaemonState.Unknown, DaemonOwner.OtherProject), gradleDaemon(DaemonState.Busy, null))

        assertEquals(listOf("other-project", null), gradleJson(daemons, attributed = true).objects("daemons").map { it.text("worktree") })
        assertEquals(listOf(null, null), gradleJson(daemons, attributed = false).objects("daemons").map { it.text("worktree") })
        assertEquals("unknown", gradleJson(daemons, attributed = true).objects("daemons").first().text("state"))
    }

    @Test
    fun `gradle json describes the build holding a daemon with or without its client and session`() {
        val session = AgentSessionRef("s1", "claude", "/w")
        val holders = listOf(
            HoldingBuild("/w", null, null),
            HoldingBuild("/w", null, GradleClient(2, listOf("build"))),
            HoldingBuild("/w", null, GradleClient(2, listOf("build"), session = session)),
        )

        val json = gradleJson(holders.map { gradleDaemon(DaemonState.Busy, null, it) }, attributed = false).objects("daemons").map { it.getValue("holder").jsonObject }

        assertEquals(listOf(JsonNull, JsonNull), json.take(2).map { it.getValue("session") })
        assertEquals(JsonNull, json.first().getValue("tasks"))
        assertEquals("s1", json.last().getValue("session").jsonObject.text("id"))
    }

    @Test
    fun `maven json names each daemon's owner only when attributed`() {
        val daemons = listOf(
            MavenDaemon(MavenDaemonProcess(1, 10, null, DaemonState.Idle(Instant.EPOCH)), DaemonOwner.RemovedWorktree),
            MavenDaemon(MavenDaemonProcess(2, 10, null), null),
        )

        val attributed = mavenJson(daemons, attributed = true).objects("daemons")

        assertEquals(listOf("removed", null), attributed.map { it.text("worktree") })
        assertEquals(listOf(Instant.EPOCH.toString(), null), attributed.map { it.text("idleSince") })
        assertEquals(listOf(null, null), mavenJson(daemons, attributed = false).objects("daemons").map { it.text("worktree") })
    }

    @Test
    fun `compose json leaves out what it doesn't know`() {
        assertEquals(setOf("worktree", "succeeded"), composeRunJson("/w", null, null).keys)
        assertEquals(setOf("worktree", "project", "succeeded", "message"), composeRunJson("/w", "p", "boom").keys)
        assertTrue("dryRun" in composeLeftoversJson(ComposeLeftoversReport(), dryRun = false))
    }

    @Test
    fun `xcode json without a listing or a trim reports nothing found`() {
        val listing = xcodeJson(active = false, listing = null)
        val trim = xcodeTrimJson(active = false, trim = null)

        assertEquals(0, listing.getValue("unavailableVolumeFolders").jsonPrimitive.content.toInt())
        assertEquals(JsonNull, listing.getValue("swiftPmCacheBytes"))
        assertEquals(JsonNull, trim.getValue("quotaMode"))
        assertEquals("false", trim.text("dryRun"))
        assertEquals(emptyList(), trim.getValue("released").jsonArray.toList())
    }

    @Test
    fun `xcode json counts a folder without a measured size as zero bytes`() {
        val folder = DerivedDataFolder("/dd/App-1", "/w/App.xcodeproj", sizeBytes = null, lastAccessed = null)
        val listing = DerivedDataListing(
            listOf(AttributedDerivedData(folder, DerivedDataOwner.Worktree("/w")), AttributedDerivedData(folder, DerivedDataOwner.Orphaned)),
            compilationCacheBytes = null,
        )

        val json = xcodeJson(active = true, listing = listing)

        assertEquals("0", json.objects("folders").single().text("sizeBytes"))
        assertEquals("0", json.objects("orphans").single().text("sizeBytes"))
    }

    @Test
    fun `device json without a device has no state`() {
        assertEquals(JsonNull, emulatorJson("/w", null, null, null, null).getValue("state"))
        assertEquals(JsonNull, simulatorJson("/w", null, null, null, null).getValue("state"))
    }

    @Test
    fun `pool json leaves a holder never used without a last use`() {
        val holder = PoolHolder(ResourceKind.EMULATOR, "/w", null, PoolHolderState.BOOTED, idle = true, lastUsed = null)

        val json = poolJson(listOf(PoolUsage(ResourceKind.EMULATOR, 1, 1, listOf(holder, holder.copy(lastUsed = Instant.EPOCH)))))

        assertEquals(listOf(null, Instant.EPOCH.toString()), json.objects("pools").single().objects("holders").map { it.text("lastUsed") })
    }

    private val config = ProjectConfig(baseRepositoryPath = "/multi/api", worktreesRoot = "", secretFiles = emptyList())

    private fun multirepo(name: String? = null, folderIsRepository: Boolean = false) = Multirepo(
        folder = "/multi",
        declaration = MultirepoDeclaration(repos = mapOf("api" to "api", "app" to "app", "web" to "web"), name = name),
        folderIsRepository = folderIsRepository,
    )

    @Test
    fun `multirepo json names each repository's worktrees folder only when it has one`() {
        val configs = mapOf("api" to config, "app" to config.copy(worktreesRoot = "/wt"), "web" to null)

        val repos = multirepoJson(multirepo(), configs, emptyMap()).objects("repos")

        assertEquals(listOf(null, "/wt", null), repos.map { it.text("worktreesFolder") })
    }

    @Test
    fun `multirepo trust json names the multirepo and its workspace when it has them`() {
        val bare = multirepoTrustJson(multirepo(), approved = false, changed = false)
        val named = multirepoTrustJson(multirepo(name = "product", folderIsRepository = true), approved = true, changed = true)

        assertFalse("name" in bare)
        assertEquals(null, bare.text("workspace"))
        assertEquals("product", named.text("name"))
        assertEquals("workspace", named.text("workspace"))
    }

    @Test
    fun `group creation json keeps a repository's error`() {
        val creation = GroupCreation("feature/TASK-1", listOf(GroupRepositoryResult("api", GroupRepositoryOutcome.FAILED, error = IllegalStateException("boom"))))

        assertEquals("boom", groupCreationJson(creation).objects("repos").single().text("error"))
    }

    private fun groupState(alias: String, status: Result<WorktreeStatus>?, base: MainBranch? = null) =
        GroupWorktreeState(GroupWorktree(alias, config, Worktree("/wt/$alias", "feature/TASK-1")), base, status, emptyList())

    @Test
    fun `group status json shows a worktree without a status, with a failed one and with one against its base`() {
        val states = listOf(
            groupState("api", null),
            groupState("app", Result.failure(IllegalStateException("git failed"))),
            groupState("web", Result.success(WorktreeStatus(0, null, null, null, base = null)), MainBranch("develop", "refs/heads/develop")),
        )
        val state = MultirepoState(MultirepoWorktrees(emptyList(), emptyList(), emptyList()), listOf(GroupState(WorktreeGroup("feature/TASK-1", emptyList()), states)), emptyList())

        val repos = groupStatusJson(multirepo(), state).objects("groups").single().objects("repos")

        assertEquals(listOf(null, null, "develop"), repos.map { it.text("base") })
        assertEquals(listOf(null, null, "0"), repos.map { it.text("uncommittedChanges") })
        assertEquals(listOf(null, null, null), repos.map { it.text("ahead") })
        assertEquals(listOf(null, null, "false"), repos.map { it.text("merged") })
        assertEquals(listOf(false, true, false), repos.map { "statusProblem" in it })
    }

    @Test
    fun `group open json reports a repository that failed to open in Orca`() {
        val openings = listOf(
            GroupRunnerOpening("api", "/wt/api", Result.failure(IllegalStateException("orca failed"))),
            GroupRunnerOpening("app", "/wt/app", Result.success(RunnerOpening(RunnerId.ORCA, RunnerOutcome.OPENED, "w1", "claude", devicesSkipped = true))),
        )

        val repos = groupOpenJson("feature/TASK-1", RunnerId.ORCA, openings).objects("repos")

        assertEquals(listOf("failed", "opened"), repos.map { it.text("result") })
        assertEquals(listOf("orca failed", null), repos.map { it.text("error") })
        assertEquals(listOf("false", "true"), repos.map { it.text("devicesSkipped") })
        assertEquals(listOf(null, "claude"), repos.map { it.text("agent") })
    }

    @Test
    fun `multirepo agent open json in Herdr says whether it opened or focused the agent`() {
        val opened = multirepoAgentOpenJson(multirepo(), RunnerOpening(RunnerId.HERDR, RunnerOutcome.OPENED, "p1", "claude"))
        val focused = multirepoAgentOpenJson(multirepo(), RunnerOpening(RunnerId.HERDR, RunnerOutcome.FOCUSED, "p1", "claude"))

        assertEquals(listOf("true", "false"), listOf(opened, focused).map { it.text("opened") })
    }

    @Test
    fun `multirepo agent open json in Orca falls back to the multirepo's agent name`() {
        val json = multirepoAgentOpenJson(multirepo(), RunnerOpening(RunnerId.ORCA, RunnerOutcome.OPENED, "w1", agent = null))

        assertFalse("opened" in json)
        assertEquals(multirepo().agent.displayName, json.text("agent"))
    }
}
