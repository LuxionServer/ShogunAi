package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.SortedView
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.domain.usecase.activityDate
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.WindowActivity
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.checkInterval
import app.luxion.shogunai.ui.actions.ActionTools
import app.luxion.shogunai.ui.actions.WorktreeActionContext
import app.luxion.shogunai.ui.actions.WorktreeCatalog
import app.luxion.shogunai.ui.actions.trayEntries
import app.luxion.shogunai.ui.actions.worktreeCatalog
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.TextInputFocus
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.domain.usecase.OrcaSyncItem
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.ui.tray.TrayWorktree
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.usecase.isActive
import app.luxion.shogunai.domain.usecase.waitsForUser
import app.luxion.shogunai.domain.usecase.worktreeActivity
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import app.luxion.shogunai.domain.usecase.GroupKept
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupWorktree
import app.luxion.shogunai.domain.usecase.GroupWorktreeState
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.selectGroupCleanup
import app.luxion.shogunai.ui.multirepo.MultirepoActions
import app.luxion.shogunai.ui.multirepo.MultirepoMembership
import app.luxion.shogunai.ui.multirepo.MultirepoNotice
import app.luxion.shogunai.ui.multirepo.NewChange
import app.luxion.shogunai.ui.multirepo.branchTypeOf
import app.luxion.shogunai.domain.usecase.GroupState as BranchGroupState
import app.luxion.shogunai.domain.usecase.WorktreeGroup as BranchGroup
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class WorktreeBoard(
    private val useCasesFor: (ProjectConfig) -> WorktreeUseCases,
    private val detectAgents: suspend (List<AgentSearch>) -> Result<Map<String, List<RunningAgent>>>,
    private val syncOrca: suspend (List<OrcaSyncItem>) -> Unit = {},
    private val preferences: ShellPreferenceRepository,
    private val cadence: suspend () -> RefreshCadence = { RefreshCadence.BALANCED },
    private val clock: () -> Instant = Instant::now,
    val approval: DialogRepositoryApproval = DialogRepositoryApproval(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val rememberFetchBeforeCreate: (projectId: String, fetch: Boolean) -> Unit = { _, _ -> },
    private val onWorktreeUsed: (WorktreeRef) -> Unit = {},
    val inactivity: InactivityPreferences = InactivityPreferences(preferences),
    private val multirepoActions: MultirepoActions = MultirepoActions.NONE,
    private val runnerInUse: () -> RunnerId = { RunnerId.HERDR },
    val desktopTurn: DesktopTurnWatch = DesktopTurnWatch.NONE,
) : ViewModel() {
    private val deviceRefreshes = mutableMapOf<String, Instant>()
    private val listPermits = Semaphore(MAX_PARALLEL_LISTS)
    private val sizeQueue = SizeQueue(viewModelScope)
    private var entries by mutableStateOf<Map<String, Entry>>(emptyMap())
    private var watchJob: Job? = null
    private val windowActivity = MutableStateFlow(WindowActivity.FOCUSED)

    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var projectFilter by mutableStateOf<String?>(null)
        private set
    var mode by mutableStateOf(GroupedView.ALL)
        private set
    private var viewProjectFilters by mutableStateOf<Map<GroupedView, String>>(emptyMap())
    private var groupings by mutableStateOf(GroupedView.entries.associateWith(preferences::loadGrouping))
    private var sorts by mutableStateOf(SortedView.entries.associateWith(preferences::loadSort))
    var textFilter by mutableStateOf("")
        private set
    var findRequests by mutableIntStateOf(0)
        private set
    var selected by mutableStateOf<WorktreeRef?>(null)
        private set
    var collapsedProjects by mutableStateOf(preferences.loadCollapsedProjects())
        private set
    var memberships by mutableStateOf<List<MultirepoMembership>>(emptyList())
        private set
    var multirepoFolder by mutableStateOf<String?>(null)
        private set
    var multirepoAgents by mutableStateOf<Map<String, List<RunningAgent>>>(emptyMap())
        private set
    var multirepoAgentsReadAt by mutableStateOf<Instant?>(null)
        private set
    private var collapsedChanges by mutableStateOf<Set<String>>(emptySet())
    private var collapsedStates by mutableStateOf(mapOf(GroupedView.ACTIVE to setOf(StateGroupKind.STOPPED)))
    var multirepoNotice by mutableStateOf<MultirepoNotice?>(null)
        private set
    var addingToGroup by mutableStateOf<Set<String>>(emptySet())
        private set
    var isOpeningMultirepoAgent by mutableStateOf(false)
        private set
    var openingGroupsInOrca by mutableStateOf<Set<String>>(emptySet())
        private set
    var isTrustingMultirepo by mutableStateOf(false)
        private set
    var newChange by mutableStateOf<NewChange?>(null)
        private set
    var cleanupOnly by mutableStateOf<Set<String>?>(null)
        private set

    var createProjectId by mutableStateOf<String?>(null)
        private set
    var createAsksForProject by mutableStateOf(false)
        private set
    var taskId by mutableStateOf("")
        private set
    var branchType by mutableStateOf(BranchType.FEATURE)
    var createMode by mutableStateOf(CreateMode.NEW_BRANCH)
        private set
    var selectedBranch by mutableStateOf<String?>(null)
    var startFrom by mutableStateOf<StartOption?>(null)
    var fetchFirst by mutableStateOf(false)

    var cleanupScope by mutableStateOf<List<String>?>(null)
        private set
    var cleanupDeleteBranches by mutableStateOf(false)
    var cleanupStopGradleDaemons by mutableStateOf(true)
    var cleanupStopMavenDaemons by mutableStateOf(true)
    var cleanupStopEmulators by mutableStateOf(true)
    var cleanupDeleteSimulators by mutableStateOf(true)
    var cleanupDeleteDerivedData by mutableStateOf(true)
    private var downComposeProjects by mutableStateOf(true)
    var cleanupDownComposeProjects: Boolean
        get() = downComposeProjects
        set(value) {
            downComposeProjects = value
            if (!value) cleanupRemoveComposeVolumes = false
        }
    var cleanupRemoveComposeVolumes by mutableStateOf(false)
    var isCleaningUp by mutableStateOf(false)
        private set
    var cleanupSuccess by mutableStateOf<WorktreeSuccess.CleanedUp?>(null)
        private set

    val viewProjectFilter: String?
        get() = viewProjectFilters[mode]

    val isProjectView: Boolean
        get() = projectFilter != null

    private val effectiveProjectFilter: String?
        get() = projectFilter ?: viewProjectFilter

    val isMultirepoView: Boolean
        get() = mode == GroupedView.MULTIREPO

    val shownMultirepo: MultirepoMembership?
        get() = multirepoFolder?.takeIf { isMultirepoView }?.let { folder -> memberships.firstOrNull { it.folder == folder } }

    val shownProjects: List<Project>
        get() = when {
            isMultirepoView -> shownMultirepo?.let { membership -> projects.filter { it.id in membership.aliases } }.orEmpty()
            else -> effectiveProjectFilter?.let { id -> projects.filter { it.id == id } } ?: projects
        }

    val isFiltered: Boolean
        get() = effectiveProjectFilter != null

    val totalWorktreeCount: Int
        get() = entries.values.sumOf { it.holder.worktrees.size }

    val isActiveView: Boolean
        get() = mode == GroupedView.ACTIVE

    val groupingOptions: List<Grouping>
        get() = if (isMultirepoView) listOf(Grouping.BY_CHANGE, Grouping.BY_STATE) else listOf(Grouping.BY_STATE, Grouping.BY_PROJECT)

    val grouping: Grouping
        get() = if (isProjectView) Grouping.BY_STATE else groupings.getValue(mode).takeIf { it in groupingOptions } ?: mode.defaultGrouping

    val sortedView: SortedView
        get() = when {
            isProjectView -> SortedView.PROJECT
            mode == GroupedView.ACTIVE -> SortedView.ACTIVE
            else -> SortedView.ALL
        }

    val sort: WorktreeSort
        get() = sorts.getValue(sortedView)

    val groups: List<WorktreeGroup>
        get() = shownProjects.mapNotNull { project ->
            val holder = holder(project.id) ?: return@mapNotNull null
            val rows = listedRows(project, holder)
            if ((isTextFiltering || isActiveView) && rows.isEmpty() && holder.listFailure == null) return@mapNotNull null
            WorktreeGroup(project, holder, rows, collapsed = project.id in collapsedProjects && !isFiltered)
        }

    val stateGroups: List<StateGroup<WorktreeEntry>>
        get() = groupByState(
            items = sortWorktreeItems(
                items = shownProjects.flatMap { project ->
                    val holder = holder(project.id) ?: return@flatMap emptyList()
                    filterWorktrees(holder.worktrees, textFilter).filterNot { isMultirepoView && it.isMain }.map { worktree ->
                        ActivityItem(WorktreeEntry(project, holder, worktree), activityOf(holder, worktree))
                    }
                },
                sort = sort,
                worktreeOf = { it.item.worktree },
                activityDateOf = { activityDateOf(it.item.holder, it.item.worktree) },
            ),
            includeInactive = !isActiveView,
            isPinned = { it.ref == selected },
        ).map { group -> group.copy(collapsed = group.kind in collapsedStates[mode].orEmpty() && !isTextFiltering) }

    fun toggleStateCollapsed(kind: StateGroupKind) {
        val current = collapsedStates[mode].orEmpty()
        val collapsing = kind !in current
        collapsedStates = collapsedStates + (mode to if (collapsing) current + kind else current - kind)
        if (collapsing && stateGroups.any { group -> group.kind == kind && group.items.any { it.ref == selected } }) selected = null
    }

    private fun expandStateGroupOf(ref: WorktreeRef) {
        if (!isGroupedByState) return
        stateGroups.firstOrNull { group -> group.collapsed && group.items.any { it.ref == ref } }?.let { toggleStateCollapsed(it.kind) }
    }

    val isGroupedByState: Boolean
        get() = grouping == Grouping.BY_STATE

    val isGroupedByChange: Boolean
        get() = isMultirepoView && grouping == Grouping.BY_CHANGE

    val isListEmpty: Boolean
        get() = when {
            isGroupedByChange -> changeView.isEmpty
            isGroupedByState -> stateGroups.isEmpty()
            else -> groups.isEmpty()
        }

    val changeView: ChangeView
        get() {
            val membership = shownMultirepo ?: return ChangeView(emptyList(), emptyList())
            val all = aliasedEntries(membership)
            val matching = filterWorktrees(all.map { it.entry.worktree }, textFilter).mapTo(mutableSetOf()) { it.path }
            val grouped = groupedBranches(all)
            val groups = grouped.map { (branch, members) ->
                ChangeGroup(
                    branch = branch,
                    entries = members.filter { it.entry.worktree.path in matching }.sortedBy { it.alias },
                    state = combinedState(members.map { activityOf(it.entry.holder, it.entry.worktree).badgeKind() }),
                    collapsed = branch in collapsedChanges && !isTextFiltering,
                    members = members.sortedBy { it.alias },
                )
            }.filter { it.entries.isNotEmpty() }.sortedBy { it.branch.lowercase() }
            val groupedPaths = grouped.values.flatten().mapTo(mutableSetOf()) { it.entry.worktree.path }
            val ungrouped = sortWorktreeItems(
                items = all.filter { it.entry.worktree.path !in groupedPaths && it.entry.worktree.path in matching },
                sort = sort,
                worktreeOf = { it.entry.worktree },
                activityDateOf = { activityDateOf(it.entry.holder, it.entry.worktree) },
            )
            return ChangeView(groups, ungrouped)
        }

    private fun aliasedEntries(membership: MultirepoMembership): List<AliasedEntry> =
        projects.filter { it.id in membership.aliases }.flatMap { project ->
            val holder = holder(project.id) ?: return@flatMap emptyList()
            val alias = membership.aliasOf(project.id) ?: return@flatMap emptyList()
            holder.worktrees.filterNot { it.isMain }.map { AliasedEntry(alias, WorktreeEntry(project, holder, it)) }
        }

    private val groupTags by derivedStateOf {
        memberships.flatMap { membership ->
            groupedBranches(aliasedEntries(membership)).flatMap { (branch, members) ->
                val aliases = members.map { it.alias }.distinct()
                members.map { it.ref to GroupTag(membership.folder, branch, aliases - it.alias) }
            }
        }.toMap()
    }

    fun groupTagOf(ref: WorktreeRef): GroupTag? = if (isMultirepoView) null else groupTags[ref]

    fun aliasOf(projectId: String): String? = shownMultirepo?.aliasOf(projectId)

    fun multirepoNameOf(projectId: String): String? = memberships.firstOrNull { projectId in it.aliases }?.name

    val isReadingActivity: Boolean
        get() = shownProjects.any { project ->
            val holder = holder(project.id) ?: return@any false
            (!holder.hasLoaded && holder.listFailure == null) ||
                holder.statuses.values.any { it == WorktreeStatusState.Loading }
        }

    val isNothingWaiting: Boolean
        get() = isActiveView && !isTextFiltering && !isReadingActivity && shownProjects.none { project ->
            val holder = holder(project.id) ?: return@none false
            holder.listFailure != null || listedRows(project, holder).any { activityOf(holder, it) == Activity.WaitingForYou }
        }

    val workingAgentCount: Int
        get() = shownProjects.sumOf { project ->
            val holder = holder(project.id) ?: return@sumOf 0
            listedRows(project, holder).sumOf { worktree ->
                holder.agents?.get(worktree.path).orEmpty().count { it.state == AgentState.WORKING || it.state == null }
            }
        }

    val waitingCount: Int
        get() = entries.values.sumOf { waitingCountOf(it.holder) }

    fun waitingCountOf(projectId: String): Int = holder(projectId)?.let(::waitingCountOf) ?: 0

    val hasClaudeWithoutState: Boolean
        get() = entries.values.any { entry ->
            entry.holder.agents.orEmpty().values.flatten().any { it.name == "claude" && it.stateSource == null }
        }

    fun multirepoAgentsOf(folder: String): List<RunningAgent> = multirepoAgents[folder].orEmpty()

    fun agentsOf(group: ChangeGroup): List<RunningAgent> =
        group.members.flatMap { it.entry.holder.agents.orEmpty()[it.entry.worktree.path].orEmpty() }

    fun agentsReadAtOf(group: ChangeGroup): Instant? =
        group.members.mapNotNull { it.entry.holder.agentsReadAt }.maxOrNull()

    fun multirepoWaitingCountOf(folder: String): Int =
        multirepoAgentsOf(folder).count { it.waitsForUser }

    private fun multirepoSearches(): List<AgentSearch> =
        memberships.filter { it.multirepo.approved }.map { AgentSearch.multirepoFolder(it.folder, it.multirepo.agent.agentCommand) }

    private fun applyMultirepoAgents(detected: Map<String, List<RunningAgent>>?) {
        val folders = memberships.map { it.folder }.toSet()
        multirepoAgents = detected?.filterKeys { it in folders }.orEmpty()
        if (detected != null) multirepoAgentsReadAt = clock()
    }

    fun agentNamesOf(projectId: String): List<String> =
        holder(projectId)?.agents.orEmpty().values.flatten().map { it.name }.distinct()

    private fun waitingCountOf(holder: ProjectWorktrees): Int =
        holder.worktrees.count { activityOf(holder, it) == Activity.WaitingForYou }

    private fun activityOf(holder: ProjectWorktrees, worktree: Worktree): Activity {
        val status = (holder.statuses[worktree.path] as? WorktreeStatusState.Loaded)?.status
        return worktreeActivity(worktree, status, holder.agents?.get(worktree.path), clock(), inactivity.threshold)
    }

    private fun orcaSyncItems(holder: ProjectWorktrees): List<OrcaSyncItem> =
        holder.worktrees.mapNotNull { worktree ->
            val status = (holder.statuses[worktree.path] as? WorktreeStatusState.Loaded)?.status ?: return@mapNotNull null
            OrcaSyncItem(worktree, status, holder.agents?.get(worktree.path).orEmpty(), activityOf(holder, worktree))
        }

    private fun activityDateOf(holder: ProjectWorktrees, worktree: Worktree): Instant? {
        val status = (holder.statuses[worktree.path] as? WorktreeStatusState.Loaded)?.status
        return activityDate(worktree, status)?.at
    }

    private fun ordered(holder: ProjectWorktrees): List<Worktree> =
        sortWorktreeItems(holder.worktrees, sort, worktreeOf = { it }, activityDateOf = { activityDateOf(holder, it) })

    private fun listedRows(project: Project, holder: ProjectWorktrees): List<Worktree> {
        val rows = filterWorktrees(ordered(holder), textFilter)
        if (!isActiveView) return rows
        return rows.filter { WorktreeRef(project.id, it.path) == selected || activityOf(holder, it).isActive }
    }

    val isTextFiltering: Boolean
        get() = textFilter.isNotBlank()

    val hasNoMatch: Boolean
        get() = isTextFiltering && isListEmpty

    val selectedWorktree: SelectedWorktree?
        get() {
            val ref = selected ?: return null
            val project = shownProjects.firstOrNull { it.id == ref.projectId } ?: return null
            val holder = holder(project.id) ?: return null
            val worktree = holder.worktrees.firstOrNull { it.path == ref.path } ?: return null
            return SelectedWorktree(project, holder, worktree)
        }

    val isCreateDialogOpen: Boolean
        get() = createProjectId != null

    val createHolder: ProjectWorktrees?
        get() = createProjectId?.let(::holder)

    val isCleanupDialogOpen: Boolean
        get() = cleanupScope != null

    val cleanupProjects: List<WorktreeGroup>
        get() = cleanupScope.orEmpty().mapNotNull { id ->
            val project = projects.firstOrNull { it.id == id } ?: return@mapNotNull null
            val holder = holder(id) ?: return@mapNotNull null
            val only = cleanupOnly
            WorktreeGroup(project, holder, holder.cleanupCandidates.filter { only == null || it.path in only }, collapsed = false)
        }

    val pendingSuccess: PendingSuccess?
        get() = cleanupSuccess?.let { PendingSuccess(null, it) }
            ?: entries.entries.firstNotNullOfOrNull { (id, entry) -> entry.holder.success?.let { PendingSuccess(id, it) } }

    val pendingRemoval: ProjectWorktrees?
        get() = entries.values.firstOrNull { it.holder.worktreePendingRemoval != null }?.holder

    val keptBranchDeletion: ProjectWorktrees?
        get() = entries.values.firstOrNull { it.holder.isConfirmingForcedBranchDeletion }?.holder

    val terminalChoice: ProjectWorktrees?
        get() = entries.values.firstOrNull { it.holder.terminalChoiceFor != null }?.holder

    var openWithRequests by mutableIntStateOf(0)
        private set

    fun selectedCatalog(strings: Strings): WorktreeCatalog? =
        selectedWorktree?.let { it.holder.actionCatalog(it.worktree, strings) }

    fun menuBarCatalog(strings: Strings): WorktreeCatalog = selectedCatalog(strings) ?: worktreeCatalog(
        WorktreeActionContext(
            tools = entries.values.firstOrNull()?.holder?.actionTools(strings) ?: ActionTools(RunnerId.entries, RunnerId.HERDR, null, null, false),
            isMain = false,
            isFolderMissing = false,
            agents = emptyList(),
            agent = null,
        ),
        strings,
    )

    fun performOnSelected(id: ActionId, agent: String? = null) {
        selectedWorktree?.let { it.holder.perform(it.worktree, id, agent) }
    }

    private fun canActOnSelection(): Boolean = !isCreateDialogOpen && !TextInputFocus.isActive && selectedWorktree != null

    fun runFromMenu(id: ActionId) {
        when (id) {
            ActionId.CopyLaunchCommand -> copySelectedLaunchCommand()
            ActionId.RemoveWorktree -> deleteSelectedWorktree()
            ActionId.OpenWith -> requestOpenWith()
            else -> if (canActOnSelection()) performOnSelected(id)
        }
    }

    fun requestOpenWith() {
        if (canActOnSelection()) openWithRequests++
    }

    val isCatalogLoaded: Boolean
        get() = projects.isNotEmpty() && entries.values.all { it.holder.hasLoaded }

    val machineProjects: List<MachineProject>
        get() = projects.mapNotNull { project ->
            val holder = holder(project.id) ?: return@mapNotNull null
            MachineProject(
                project = project,
                worktrees = holder.worktrees.takeIf { holder.hasLoaded },
                declares = holder.declaredResources,
                gradleSettings = holder.gradleSettings,
                gradleBuildCacheEnabled = holder.isGradleBuildCacheEnabled,
                compilationCache = holder.hasCompilationCache,
            )
        }

    fun holder(projectId: String): ProjectWorktrees? = entries[projectId]?.holder

    val paletteWorktrees: List<PaletteWorktree>
        get() = projects.flatMap { project ->
            val holder = holder(project.id) ?: return@flatMap emptyList()
            ordered(holder).map { worktree ->
                val kind = activityOf(holder, worktree).badgeKind()
                PaletteWorktree(project, worktree, kind.takeIf { it != StateBadgeKind.OTHER })
            }
        }

    val actionErrors: Map<WorktreeRef, Throwable>
        get() = entries.values.flatMap { entry ->
            entry.holder.actionErrors.map { (path, failure) -> WorktreeRef(entry.project.id, path) to failure }
        }.toMap()

    fun perform(ref: WorktreeRef, id: ActionId) {
        val holder = holder(ref.projectId) ?: return
        holder.worktrees.firstOrNull { it.path == ref.path }?.let { holder.perform(it, id) }
    }

    fun trayWorktrees(recents: List<RecentWorktree>, strings: Strings): List<TrayWorktree> =
        recents.mapNotNull { recent ->
            val project = projects.firstOrNull { it.id == recent.projectId } ?: return@mapNotNull null
            val holder = holder(project.id)
            val worktree = holder?.worktrees?.firstOrNull { it.path == recent.path }
            if (holder?.hasLoaded == true && worktree == null) return@mapNotNull null
            TrayWorktree(
                ref = WorktreeRef(project.id, recent.path),
                name = worktree?.branch ?: recent.path.replace('\\', '/').trimEnd('/').substringAfterLast('/'),
                projectName = project.name,
                activity = if (holder != null && worktree != null) activityOf(holder, worktree) else Activity.Unknown,
                isLoaded = worktree != null,
                actions = worktree?.let { holder.actionCatalog(it, strings).trayEntries() },
            )
        }

    fun copyLaunchCommand(ref: WorktreeRef, onCopied: () -> Unit = {}) {
        val holder = holder(ref.projectId) ?: return
        holder.worktrees.firstOrNull { it.path == ref.path }?.let { holder.copyLaunchCommand(it, onCopied) }
    }

    fun syncCatalog(catalog: List<Project>) {
        projects = catalog
        val current = entries
        val next = catalog.associate { project ->
            val existing = current[project.id]
            if (existing != null && existing.project.config.withoutFetchPreference() == project.config.withoutFetchPreference()) {
                project.id to existing.copy(project = project)
            } else {
                existing?.dispose()
                deviceRefreshes.remove(project.id)
                project.id to createEntry(project)
            }
        }
        current.filterKeys { it !in next }.values.forEach { it.dispose() }
        entries = next
        if (selected?.projectId?.let { it !in next } == true) selected = null
        if (projectFilter?.let { it !in next } == true) projectFilter = null
        viewProjectFilters = viewProjectFilters.filterValues { it in next }
        if (createProjectId?.let { it !in next } == true) createProjectId = null
        cleanupScope = cleanupScope?.filter { it in next }?.takeIf { it.isNotEmpty() }
        sizeQueue.prioritize(shownHolders())
    }

    private fun createEntry(project: Project): Entry {
        val job = SupervisorJob(viewModelScope.coroutineContext[Job])
        val holder = ProjectWorktrees(
            useCases = useCasesFor(project.config),
            scope = CoroutineScope(viewModelScope.coroutineContext + job),
            listPermits = listPermits,
            sizeQueue = sizeQueue,
            clock = clock,
            onRemoved = { path -> if (selected == WorktreeRef(project.id, path)) selected = null },
            onUsed = { path -> onWorktreeUsed(WorktreeRef(project.id, path)) },
            ioDispatcher = ioDispatcher,
        )
        holder.refresh()
        return Entry(project, holder, job)
    }

    fun syncMultirepos(memberships: List<MultirepoMembership>) {
        this.memberships = memberships
        sizeQueue.prioritize(shownHolders())
    }

    fun showMultirepo(folder: String) {
        mode = GroupedView.MULTIREPO
        projectFilter = null
        multirepoFolder = folder
        sizeQueue.prioritize(shownHolders())
        viewModelScope.launch { applyMultirepoAgents(detectAgents(multirepoSearches()).getOrNull()) }
    }

    fun revealGroup(branch: String) {
        clearTextFilter()
        collapsedChanges = collapsedChanges - branch
        val group = changeView.groups.firstOrNull { it.branch == branch } ?: return
        if (group.entries.none { it.ref == selected }) group.entries.firstOrNull()?.let { select(it.ref) }
    }

    fun toggleChangeCollapsed(branch: String) {
        collapsedChanges = if (branch in collapsedChanges) collapsedChanges - branch else collapsedChanges + branch
        if (branch in collapsedChanges && changeView.groups.any { group -> group.branch == branch && group.entries.any { it.ref == selected } }) {
            selected = null
        }
    }

    fun openAllInHerdr(group: ChangeGroup) {
        group.members.forEach { it.entry.holder.openInHerdr(it.entry.worktree) }
    }

    val isOrcaInstalled: Boolean get() = multirepoActions.orcaInstalled

    val defaultRunner: RunnerId
        get() = entries.values.firstOrNull()?.holder?.tools?.defaultRunner ?: RunnerId.HERDR

    fun openAllInOrca(group: ChangeGroup) {
        if (group.branch in openingGroupsInOrca) return
        openingGroupsInOrca = openingGroupsInOrca + group.branch
        val members = group.members
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { multirepoActions.openGroup(RunnerId.ORCA, branchGroupOf(group)) }
                    .onSuccess { openings ->
                        openings.forEach { opening ->
                            members.firstOrNull { it.entry.worktree.path == opening.path }?.entry?.holder?.showOpenResult(opening.path, opening.result)
                        }
                        val outcomes = openings.mapNotNull { opening -> opening.result.getOrNull()?.let { opening.alias to it.outcome } }
                        multirepoNotice = if (outcomes.isEmpty()) {
                            openings.firstNotNullOfOrNull { it.result.exceptionOrNull() }?.let(MultirepoNotice::Failed)
                        } else {
                            MultirepoNotice.GroupOpenedInOrca(group.title, outcomes)
                        }
                    }
                    .onFailure { multirepoNotice = MultirepoNotice.Failed(it) }
            } finally {
                openingGroupsInOrca = openingGroupsInOrca - group.branch
            }
        }
    }

    private fun branchGroupOf(group: ChangeGroup): BranchGroup =
        BranchGroup(group.branch, group.members.map { GroupWorktree(it.alias, it.entry.project.config, it.entry.worktree) })

    fun missingAliases(group: ChangeGroup): List<String> =
        shownMultirepo?.aliases?.values?.filter { it !in group.aliases }?.sorted().orEmpty()

    fun canAddRepositories(group: ChangeGroup): Boolean = branchTypeOf(group.branch) != null && missingAliases(group).isNotEmpty()

    fun addRepositoryToGroup(group: ChangeGroup, alias: String) {
        val membership = shownMultirepo ?: return
        val (type, id) = branchTypeOf(group.branch) ?: return
        val projectId = membership.projectIdOf(alias) ?: return
        val project = projects.firstOrNull { it.id == projectId } ?: return
        val key = "${group.branch}:$alias"
        if (key in addingToGroup) return
        addingToGroup = addingToGroup + key
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    multirepoActions.createGroup(listOf(MultirepoMember(alias, project.config)), id, type, false) {}
                }
                val repository = result.getOrNull()?.repositories?.singleOrNull()
                multirepoNotice = when {
                    result.isFailure -> MultirepoNotice.Failed(result.exceptionOrNull()!!)
                    repository?.outcome == GroupRepositoryOutcome.FAILED -> MultirepoNotice.Failed(repository.error ?: IllegalStateException(alias))
                    else -> MultirepoNotice.AddedToGroup(alias, group.title)
                }
                holder(projectId)?.refresh()
            } finally {
                addingToGroup = addingToGroup - key
            }
        }
    }

    fun groupCleanupBlocker(group: ChangeGroup): GroupKept? {
        val branchGroup = branchGroupOf(group)
        val states = group.members.zip(branchGroup.worktrees).map { (member, worktree) ->
            val holder = member.entry.holder
            val path = member.entry.worktree.path
            val status = when (val state = holder.statuses[path]) {
                is WorktreeStatusState.Loaded -> Result.success(state.status)
                WorktreeStatusState.Unavailable -> Result.failure(IllegalStateException(path))
                else -> null
            }
            GroupWorktreeState(worktree, holder.baseOf(member.entry.worktree), status, holder.agents.orEmpty()[path].orEmpty())
        }
        return selectGroupCleanup(listOf(BranchGroupState(branchGroup, states))).kept.firstOrNull()
    }

    fun groupCleanUpBlock(group: ChangeGroup): CleanupBlock? = when {
        isCleaningUp -> CleanupBlock.Running
        else -> groupCleanupBlocker(group)?.let(CleanupBlock::Kept)
            ?: CleanupBlock.NothingToCleanUp.takeUnless {
                group.members.all { member -> member.entry.holder.cleanupCandidates.any { it.path == member.entry.worktree.path } }
            }
    }

    fun canCleanUpGroup(group: ChangeGroup): Boolean = groupCleanUpBlock(group) == null

    fun openGroupCleanup(group: ChangeGroup) {
        if (!canCleanUpGroup(group)) return
        resetCleanupOptions()
        cleanupOnly = group.members.mapTo(mutableSetOf()) { it.entry.worktree.path }
        cleanupScope = group.members.map { it.entry.project.id }.distinct().also { ids ->
            ids.forEach { holder(it)?.lookUpCleanupCandidatesInHerdr() }
        }
    }

    fun openMultirepoAgent() {
        val multirepo = shownMultirepo?.multirepo?.takeIf { it.approved } ?: return
        if (isOpeningMultirepoAgent) return
        isOpeningMultirepoAgent = true
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { multirepoActions.openAgent(RunnerId.HERDR, multirepo) }
                    .onSuccess { multirepoNotice = MultirepoNotice.AgentOpened(it.runner, it.outcome) }
                    .onFailure { multirepoNotice = MultirepoNotice.Failed(it) }
            } finally {
                isOpeningMultirepoAgent = false
            }
        }
    }

    fun openMultirepoAgentInOrca() {
        val multirepo = shownMultirepo?.multirepo?.takeIf { it.approved } ?: return
        if (isOpeningMultirepoAgent) return
        isOpeningMultirepoAgent = true
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { multirepoActions.openAgent(RunnerId.ORCA, multirepo) }
                    .onSuccess { multirepoNotice = MultirepoNotice.AgentOpened(it.runner, it.outcome) }
                    .onFailure { multirepoNotice = MultirepoNotice.Failed(it) }
            } finally {
                isOpeningMultirepoAgent = false
            }
        }
    }

    fun dismissMultirepoNotice() {
        multirepoNotice = null
    }

    fun openNewChange() {
        val membership = shownMultirepo?.takeIf { it.multirepo.approved } ?: return
        if (newChange == null) newChange = NewChange(membership, multirepoActions.installedRunners, runnerInUse())
    }

    fun trustShownMultirepo(onTrusted: () -> Unit) {
        val multirepo = shownMultirepo?.multirepo?.takeIf { !it.approved } ?: return
        if (isTrustingMultirepo) return
        isTrustingMultirepo = true
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { multirepoActions.trust(multirepo) }
                    .onSuccess { onTrusted() }
                    .onFailure { multirepoNotice = MultirepoNotice.Failed(it) }
            } finally {
                isTrustingMultirepo = false
            }
        }
    }

    fun dismissNewChange() {
        if (newChange?.isCreating != true) newChange = null
    }

    fun confirmNewChange() {
        val change = newChange ?: return
        if (!change.canConfirm) return
        val members = change.orderedSelection.mapNotNull { alias ->
            val project = change.membership.projectIdOf(alias)?.let { id -> projects.firstOrNull { it.id == id } } ?: return@mapNotNull null
            MultirepoMember(alias, project.config)
        }
        change.isCreating = true
        change.error = null
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    multirepoActions.createGroup(members, change.taskId, change.branchType, change.fetchFirst) {}
                }.onSuccess { creation ->
                    change.results = creation.repositories
                    members.forEach { member -> change.membership.projectIdOf(member.alias)?.let(::holder)?.refresh() }
                    val runner = change.runner
                    if (runner != null) {
                        change.openings = withContext(ioDispatcher) { openCreated(runner, members, creation) }
                    } else if (!creation.failed) {
                        multirepoNotice = MultirepoNotice.ChangeCreated(changeTitle(creation.branch), members.map { it.alias })
                        if (newChange === change) newChange = null
                    }
                }.onFailure { change.error = it }
            } finally {
                change.isCreating = false
            }
        }
    }

    private suspend fun openCreated(runner: RunnerId, members: List<MultirepoMember>, creation: GroupCreation): Map<String, Result<RunnerOpening>> {
        val worktrees = creation.repositories.mapNotNull { result ->
            val path = result.path ?: return@mapNotNull null
            val member = members.firstOrNull { it.alias == result.alias } ?: return@mapNotNull null
            GroupWorktree(result.alias, member.config, Worktree(path = path, branch = creation.branch))
        }
        val group = BranchGroup(creation.branch, worktrees)
        return multirepoActions.openGroup(runner, group).fold(
            onSuccess = { openings -> openings.associate { opening -> opening.alias to opening.result } },
            onFailure = { error -> worktrees.associate { it.alias to Result.failure(error) } },
        )
    }

    fun show(projectFilter: String?, mode: GroupedView = GroupedView.ALL) {
        this.mode = mode
        this.projectFilter = projectFilter?.takeIf { id -> mode == GroupedView.ALL && projects.any { it.id == id } }
        sizeQueue.prioritize(shownHolders())
    }

    fun updateViewProjectFilter(projectId: String?) {
        viewProjectFilters = if (projectId != null && projects.any { it.id == projectId }) {
            viewProjectFilters + (mode to projectId)
        } else {
            viewProjectFilters - mode
        }
        sizeQueue.prioritize(shownHolders())
    }

    fun updateGrouping(grouping: Grouping) {
        groupings = groupings + (mode to grouping)
        preferences.saveGrouping(mode, grouping)
    }

    fun updateSort(sort: WorktreeSort) {
        sorts = sorts + (sortedView to sort)
        preferences.saveSort(sortedView, sort)
    }

    fun updateTextFilter(text: String) {
        textFilter = text
    }

    fun clearTextFilter() {
        textFilter = ""
    }

    fun requestFind() {
        findRequests++
    }

    fun refreshShown() {
        shownHolders().forEach { holder ->
            holder.refresh(remeasureSizes = true)
            if (isCreateDialogOpen && createHolder == holder) holder.loadBranches()
        }
    }

    fun refreshOnFocus() {
        val due = shownEntries().filter { isDueForFocusRefresh(it.holder) }
        if (due.isEmpty()) return
        due.forEach { it.holder.refreshStatuses() }
        viewModelScope.launch {
            val searches = due.map { AgentSearch(it.project.config.agents.map { agent -> agent.agentCommand }, it.holder.worktrees) }
            val detected = detectAgents(searches + multirepoSearches()).getOrNull()
            due.forEach { it.holder.applyAgents(detected) }
            applyMultirepoAgents(detected)
        }
    }

    private fun isDueForFocusRefresh(holder: ProjectWorktrees): Boolean {
        if (holder.isBusy || !holder.hasLoaded) return false
        val lastRead = holder.lastReadAt ?: return true
        return clock() >= lastRead + FOCUS_REFRESH_MIN_INTERVAL.toJavaDuration()
    }

    fun repairAfterMove(projectId: String, previousWorktreesRoot: String) {
        holder(projectId)?.repairAfterMove(previousWorktreesRoot)
    }

    fun startWatching() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            var check: Job? = null
            while (true) {
                val activity = windowActivity.first { it.isShown }
                val current = cadence()
                val changed = withTimeoutOrNull(current.checkInterval(activity)) { windowActivity.first { it != activity } }
                if (changed != null) continue
                if (check?.isActive != true) check = launch { checkProjects(current.devices) }
            }
        }
    }

    fun setWindowActivity(activity: WindowActivity) {
        windowActivity.value = activity
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun checkProjects(deviceRefreshInterval: Duration) {
        desktopTurn.refresh()
        val unchanged = shownEntries()
            .filterNot { it.holder.isBusy }
            .filter { it.holder.checkFingerprint() == FingerprintCheck.UNCHANGED }
        val shownIds = shownProjects.map { it.id }.toSet()
        val hidden = entries.values.filter { it.project.id !in shownIds && !it.holder.isBusy && it.holder.hasLoaded }
        val loaded = unchanged + hidden
        val searches = loaded.map { AgentSearch(it.project.config.agents.map { agent -> agent.agentCommand }, it.holder.worktrees) } + multirepoSearches()
        if (searches.isEmpty()) return
        val detected = detectAgents(searches).getOrNull()
        loaded.forEach { it.holder.applyAgents(detected) }
        applyMultirepoAgents(detected)
        syncOrca(loaded.flatMap { orcaSyncItems(it.holder) })
        unchanged.forEach { entry ->
            if (isDeviceRefreshDue(entry.project.id, deviceRefreshInterval)) entry.holder.refreshDevices()
        }
    }

    private fun isDeviceRefreshDue(projectId: String, deviceRefreshInterval: Duration): Boolean {
        val now = clock()
        val last = deviceRefreshes[projectId]
        if (last != null && (now.toEpochMilli() - last.toEpochMilli()).milliseconds < deviceRefreshInterval) return false
        deviceRefreshes[projectId] = now
        return true
    }

    fun select(ref: WorktreeRef) {
        expandStateGroupOf(ref)
        selected = ref
        onWorktreeUsed(ref)
    }

    fun revealInAllWorktrees(ref: WorktreeRef) {
        clearTextFilter()
        if (viewProjectFilters[GroupedView.ALL]?.let { it != ref.projectId } == true) {
            viewProjectFilters = viewProjectFilters - GroupedView.ALL
        }
        if (ref.projectId in collapsedProjects) toggleCollapsed(ref.projectId)
        select(ref)
    }

    fun clearSelection() {
        selected = null
    }

    fun escape() {
        if (textFilter.isNotEmpty()) clearTextFilter() else clearSelection()
    }

    fun selectNext() = moveSelection(1)

    fun selectPrevious() = moveSelection(-1)

    private fun moveSelection(step: Int) {
        val visible = visibleRefs()
        if (visible.isEmpty()) return
        val index = visible.indexOf(selected)
        val target = if (index < 0) {
            if (step > 0) 0 else visible.lastIndex
        } else {
            (index + step).coerceIn(0, visible.lastIndex)
        }
        selected = visible[target]
    }

    fun visibleRefs(): List<WorktreeRef> =
        if (isGroupedByChange) {
            changeView.let { view -> view.groups.filterNot { it.collapsed }.flatMap { group -> group.entries.map { it.ref } } + view.ungrouped.map { it.ref } }
        } else if (isGroupedByState) {
            stateGroups.filterNot { it.collapsed }.flatMap { group -> group.items.map { it.ref } }
        } else {
            groups.filterNot { it.collapsed }.flatMap { group -> group.worktrees.map { WorktreeRef(group.project.id, it.path) } }
        }

    fun toggleCollapsed(projectId: String) {
        collapsedProjects = if (projectId in collapsedProjects) collapsedProjects - projectId else collapsedProjects + projectId
        preferences.saveCollapsedProjects(collapsedProjects)
        if (selected?.projectId == projectId && projectId in collapsedProjects) selected = null
    }

    fun openCreateDialog(projectId: String? = null) {
        if (isCreateDialogOpen) return
        val target = projectId ?: effectiveProjectFilter ?: defaultCreateProject() ?: return
        createAsksForProject = projectId == null && effectiveProjectFilter == null
        taskId = ""
        selectedBranch = null
        startFrom = null
        fetchFirst = fetchPreferenceOf(target)
        holder(target)?.dismissCreateError()
        createProjectId = target
        holder(target)?.loadBranches()
    }

    private fun defaultCreateProject(): String? =
        preferences.loadLastCreateProject()?.takeIf { id -> projects.any { it.id == id } } ?: projects.firstOrNull()?.id

    fun selectCreateProject(projectId: String) {
        if (createHolder?.isCreating == true || holder(projectId) == null) return
        createProjectId = projectId
        selectedBranch = null
        startFrom = null
        fetchFirst = fetchPreferenceOf(projectId)
        holder(projectId)?.dismissCreateError()
        holder(projectId)?.loadBranches()
    }

    private fun fetchPreferenceOf(projectId: String): Boolean =
        projects.firstOrNull { it.id == projectId }?.config?.fetchBeforeCreate ?: false

    fun updateCreateMode(mode: CreateMode) {
        createMode = mode
    }

    val startOptions: List<StartOption>
        get() {
            val holder = createHolder ?: return emptyList()
            val default = holder.defaultStartRef
            val branches = holder.branches.map { it.name }.filter { it != default }
            return listOfNotNull(StartOption.Current, default?.let(StartOption::Branch)) + branches.map(StartOption::Branch)
        }

    val effectiveStartFrom: StartOption
        get() = startFrom ?: StartOption.Current

    val currentBranchName: String?
        get() = createHolder?.worktrees?.firstOrNull { it.isMain }?.branch

    val canFetchFirst: Boolean
        get() = createHolder?.hasOrigin == true

    fun updateTaskId(raw: String) {
        taskId = normalizeTaskId(raw)
    }

    fun isTaskIdValid(taskId: String): Boolean = isValidGitRefSegment(normalizeTaskId(taskId))

    val canCreate: Boolean
        get() {
            val holder = createHolder ?: return false
            return !holder.isCreating && !holder.isLoadingBranches && holder.createdWithErrorPath == null && isCreateEnabled(
                mode = createMode,
                isTaskIdValid = taskId.isNotBlank() && isTaskIdValid(taskId),
                isSelectedBranchAvailable = holder.isBranchAvailable(selectedBranch),
            )
        }

    fun dismissCreateDialog() {
        if (createHolder?.isCreating != true) createProjectId = null
    }

    fun confirmCreate(skipFetch: Boolean = false) {
        if (!canCreate) return
        val projectId = createProjectId ?: return
        val holder = holder(projectId) ?: return
        if (holder.hasOrigin && fetchFirst != fetchPreferenceOf(projectId)) rememberFetchBeforeCreate(projectId, fetchFirst)
        val fetch = fetchFirst && holder.hasOrigin && !skipFetch
        val onCreated: (Worktree) -> Unit = {
            preferences.saveLastCreateProject(projectId)
            if (createProjectId == projectId) createProjectId = null
        }
        when (createMode) {
            CreateMode.NEW_BRANCH -> holder.create(taskId, branchType, effectiveStartFrom.ref, fetch, onCreated)
            CreateMode.EXISTING_BRANCH -> selectedBranch?.let { holder.createFromBranch(it, fetch, onCreated) }
        }
    }

    fun cleanUpBlock(projectId: String? = null): CleanupBlock? = when {
        isCleaningUp -> CleanupBlock.Running
        cleanupTargets(projectId).isEmpty() -> CleanupBlock.NothingToCleanUp
        else -> null
    }

    fun canCleanUp(projectId: String? = null): Boolean = cleanUpBlock(projectId) == null

    private fun cleanupTargets(projectId: String?): List<String> =
        (projectId?.let(::listOf) ?: shownProjects.map { it.id }).filter { holder(it)?.canCleanUp == true }

    fun openCleanup(projectId: String? = null) {
        if (!canCleanUp(projectId)) return
        resetCleanupOptions()
        cleanupOnly = null
        cleanupScope = cleanupTargets(projectId).also { targets ->
            targets.forEach { holder(it)?.lookUpCleanupCandidatesInHerdr() }
        }
    }

    private fun resetCleanupOptions() {
        cleanupDeleteBranches = false
        cleanupStopGradleDaemons = true
        cleanupStopMavenDaemons = true
        cleanupStopEmulators = true
        cleanupDeleteSimulators = true
        cleanupDeleteDerivedData = true
        cleanupDownComposeProjects = true
        cleanupRemoveComposeVolumes = false
    }

    fun dismissCleanup() {
        if (isCleaningUp) return
        cleanupScope = null
        cleanupOnly = null
    }

    fun confirmCleanup() {
        val holders = cleanupProjects.map { it.holder }
        if (holders.isEmpty() || isCleaningUp) return
        val options = CleanupOptions(
            deleteBranches = cleanupDeleteBranches,
            stopGradleDaemons = cleanupStopGradleDaemons,
            stopMavenDaemons = cleanupStopMavenDaemons,
            stopEmulators = cleanupStopEmulators,
            deleteSimulators = cleanupDeleteSimulators,
            deleteDerivedData = cleanupDeleteDerivedData,
            downComposeProjects = cleanupDownComposeProjects,
            removeComposeVolumes = cleanupDownComposeProjects && cleanupRemoveComposeVolumes,
        )
        val only = cleanupOnly
        isCleaningUp = true
        viewModelScope.launch {
            try {
                cleanupSuccess = holders.mapNotNull { it.cleanUp(options, only) }.reduceOrNull { total, next -> total + next }
            } finally {
                isCleaningUp = false
                cleanupScope = null
                cleanupOnly = null
            }
        }
    }

    fun dismissSuccess(pending: PendingSuccess) {
        if (pending.projectId == null) cleanupSuccess = null else holder(pending.projectId)?.dismissSuccess()
    }

    fun copySelectedLaunchCommand() {
        if (isCreateDialogOpen || TextInputFocus.isActive) return
        selectedWorktree?.let { it.holder.copyLaunchCommand(it.worktree) }
    }

    fun deleteSelectedWorktree() {
        if (isCreateDialogOpen || isCleanupDialogOpen || pendingRemoval != null || keptBranchDeletion != null || terminalChoice != null || TextInputFocus.isActive) return
        selectedWorktree?.takeIf { !it.worktree.isMain }?.let { it.holder.requestRemoval(it.worktree) }
    }

    private fun shownEntries(): List<Entry> = shownProjects.mapNotNull { entries[it.id] }

    private fun shownHolders(): Set<ProjectWorktrees> = shownEntries().map { it.holder }.toSet()

    override fun onCleared() {
        entries.values.forEach { it.dispose() }
    }

    private data class Entry(val project: Project, val holder: ProjectWorktrees, val job: Job) {
        fun dispose() {
            holder.dispose()
            job.cancel()
        }
    }
}

data class WorktreeRef(val projectId: String, val path: String)

data class WorktreeGroup(
    val project: Project,
    val holder: ProjectWorktrees,
    val worktrees: List<Worktree>,
    val collapsed: Boolean,
)

data class WorktreeEntry(val project: Project, val holder: ProjectWorktrees, val worktree: Worktree) {
    val ref = WorktreeRef(project.id, worktree.path)
}

data class PaletteWorktree(val project: Project, val worktree: Worktree, val indicator: StateBadgeKind?)

data class SelectedWorktree(val project: Project, val holder: ProjectWorktrees, val worktree: Worktree)

data class PendingSuccess(val projectId: String?, val success: WorktreeSuccess)

sealed interface StartOption {
    val ref: String?

    data object Current : StartOption {
        override val ref: String? = null
    }

    data class Branch(override val ref: String) : StartOption
}

private fun ProjectConfig.withoutFetchPreference(): ProjectConfig = copy(fetchBeforeCreate = false)

internal fun isCreateEnabled(
    mode: CreateMode,
    isTaskIdValid: Boolean,
    isSelectedBranchAvailable: Boolean,
): Boolean = when (mode) {
    CreateMode.NEW_BRANCH -> isTaskIdValid
    CreateMode.EXISTING_BRANCH -> isSelectedBranchAvailable
}

private const val MAX_PARALLEL_LISTS = 2
private val FOCUS_REFRESH_MIN_INTERVAL = 5.seconds

sealed interface CleanupBlock {
    data object Running : CleanupBlock
    data object NothingToCleanUp : CleanupBlock
    data object NeedsReview : CleanupBlock
    data class Kept(val kept: GroupKept) : CleanupBlock
}
