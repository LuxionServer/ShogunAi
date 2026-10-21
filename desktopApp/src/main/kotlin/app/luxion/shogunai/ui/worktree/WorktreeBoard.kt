package app.luxion.shogunai.ui.worktree

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
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.TextInputFocus
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.ui.tray.TrayOpenAction
import app.luxion.shogunai.ui.tray.TrayWorktree
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.usecase.isActive
import app.luxion.shogunai.domain.usecase.worktreeActivity
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val preferences: ShellPreferenceRepository,
    private val autoRefreshInterval: Duration = AUTO_REFRESH_INTERVAL,
    private val clock: () -> Instant = Instant::now,
    private val deviceRefreshInterval: Duration = DEVICE_REFRESH_INTERVAL,
    val approval: DialogPostCreateApproval = DialogPostCreateApproval(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val rememberFetchBeforeCreate: (projectId: String, fetch: Boolean) -> Unit = { _, _ -> },
    private val onWorktreeUsed: (WorktreeRef) -> Unit = {},
    val inactivity: InactivityPreferences = InactivityPreferences(preferences),
) : ViewModel() {
    private val deviceRefreshes = mutableMapOf<String, Instant>()
    private val listPermits = Semaphore(MAX_PARALLEL_LISTS)
    private val sizeQueue = SizeQueue(viewModelScope)
    private var entries by mutableStateOf<Map<String, Entry>>(emptyMap())
    private var watchJob: Job? = null
    private val windowActive = MutableStateFlow(true)

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
    var cleanupStopEmulators by mutableStateOf(true)
    var cleanupDeleteSimulators by mutableStateOf(true)
    var cleanupDeleteDerivedData by mutableStateOf(true)
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

    val shownProjects: List<Project>
        get() = effectiveProjectFilter?.let { id -> projects.filter { it.id == id } } ?: projects

    val isFiltered: Boolean
        get() = effectiveProjectFilter != null

    val totalWorktreeCount: Int
        get() = entries.values.sumOf { it.holder.worktrees.size }

    val isActiveView: Boolean
        get() = mode == GroupedView.ACTIVE

    val grouping: Grouping
        get() = if (isProjectView) Grouping.BY_STATE else groupings.getValue(mode)

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
                    filterWorktrees(holder.worktrees, textFilter).map { worktree ->
                        ActivityItem(WorktreeEntry(project, holder, worktree), activityOf(holder, worktree))
                    }
                },
                sort = sort,
                worktreeOf = { it.item.worktree },
                activityDateOf = { activityDateOf(it.item.holder, it.item.worktree) },
            ),
            includeInactive = !isActiveView,
            isPinned = { it.ref == selected },
        )

    val isGroupedByState: Boolean
        get() = grouping == Grouping.BY_STATE

    private val isListEmpty: Boolean
        get() = if (isGroupedByState) stateGroups.isEmpty() else groups.isEmpty()

    val isReadingActivity: Boolean
        get() = shownProjects.any { project ->
            val holder = holder(project.id) ?: return@any false
            (!holder.hasLoaded && holder.listFailure == null) ||
                holder.statuses.values.any { it == WorktreeStatusState.Loading }
        }

    val isNothingActive: Boolean
        get() = isActiveView && !isTextFiltering && !isReadingActivity && isListEmpty && shownProjects.none {
            holder(it.id)?.listFailure != null
        }

    val activeCount: Int
        get() = entries.values.sumOf { activeCountOf(it.holder) }

    fun activeCountOf(projectId: String): Int = holder(projectId)?.let(::activeCountOf) ?: 0

    fun agentNamesOf(projectId: String): List<String> =
        holder(projectId)?.agents.orEmpty().values.flatten().map { it.name }.distinct()

    private fun activeCountOf(holder: ProjectWorktrees): Int =
        holder.worktrees.count { activityOf(holder, it).isActive }

    private fun activityOf(holder: ProjectWorktrees, worktree: Worktree): Activity {
        val status = (holder.statuses[worktree.path] as? WorktreeStatusState.Loaded)?.status
        return worktreeActivity(worktree, status, holder.agents?.get(worktree.path), clock(), inactivity.threshold)
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
            WorktreeGroup(project, holder, holder.cleanupCandidates, collapsed = false)
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

    fun selectedOpenActions(strings: Strings): List<OpenAction> =
        selectedWorktree?.let { it.holder.openActions(it.worktree, strings) }.orEmpty()

    fun selectedLaunchChoices(strings: Strings): List<LaunchChoice> =
        selectedWorktree?.let { it.holder.launchChoices(it.worktree, strings) }.orEmpty()

    fun launchSelected(kind: LaunchKind, agentName: String) {
        val selected = selectedWorktree ?: return
        val agent = selected.holder.agentOptions.firstOrNull { it.displayName == agentName } ?: return
        selected.holder.launch(selected.worktree, kind, agent)
    }

    fun openSelected(kind: OpenKind) {
        selectedWorktree?.let { it.holder.open(it.worktree, kind) }
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

    fun open(ref: WorktreeRef, kind: OpenKind) {
        val holder = holder(ref.projectId) ?: return
        holder.worktrees.firstOrNull { it.path == ref.path }?.let { holder.open(it, kind) }
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
                openActions = worktree?.let { holder.openActions(it, strings).map { action -> TrayOpenAction(action.kind, action.label) } }.orEmpty(),
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
            val detected = detectAgents(searches).getOrNull()
            due.forEach { it.holder.applyAgents(detected) }
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
                windowActive.first { it }
                delay(autoRefreshInterval)
                if (!windowActive.value) continue
                if (check?.isActive != true) check = launch { checkProjects() }
            }
        }
    }

    fun setWindowActive(active: Boolean) {
        windowActive.value = active
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun checkProjects() {
        val unchanged = shownEntries()
            .filterNot { it.holder.isBusy }
            .filter { it.holder.checkFingerprint() == FingerprintCheck.UNCHANGED }
        val shownIds = shownProjects.map { it.id }.toSet()
        val hidden = entries.values.filter { it.project.id !in shownIds && !it.holder.isBusy && it.holder.hasLoaded }
        val loaded = unchanged + hidden
        if (loaded.isEmpty()) return
        val searches = loaded.map { AgentSearch(it.project.config.agents.map { agent -> agent.agentCommand }, it.holder.worktrees) }
        val detected = detectAgents(searches).getOrNull()
        loaded.forEach { it.holder.applyAgents(detected) }
        unchanged.forEach { entry ->
            if (isDeviceRefreshDue(entry.project.id)) entry.holder.refreshDevices()
        }
    }

    private fun isDeviceRefreshDue(projectId: String): Boolean {
        val now = clock()
        val last = deviceRefreshes[projectId]
        if (last != null && (now.toEpochMilli() - last.toEpochMilli()).milliseconds < deviceRefreshInterval) return false
        deviceRefreshes[projectId] = now
        return true
    }

    fun select(ref: WorktreeRef) {
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
        if (isGroupedByState) {
            stateGroups.flatMap { group -> group.items.map { it.ref } }
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
            return listOfNotNull(default?.let(StartOption::Branch), StartOption.Current) + branches.map(StartOption::Branch)
        }

    val effectiveStartFrom: StartOption
        get() = startFrom ?: createHolder?.defaultStartRef?.let(StartOption::Branch) ?: StartOption.Current

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

    fun canCleanUp(projectId: String? = null): Boolean =
        !isCleaningUp && cleanupTargets(projectId).isNotEmpty()

    private fun cleanupTargets(projectId: String?): List<String> =
        (projectId?.let(::listOf) ?: shownProjects.map { it.id }).filter { holder(it)?.canCleanUp == true }

    fun openCleanup(projectId: String? = null) {
        if (!canCleanUp(projectId)) return
        cleanupDeleteBranches = false
        cleanupStopGradleDaemons = true
        cleanupStopEmulators = true
        cleanupDeleteSimulators = true
        cleanupDeleteDerivedData = true
        cleanupScope = cleanupTargets(projectId).also { targets ->
            targets.forEach { holder(it)?.lookUpCleanupCandidatesInHerdr() }
        }
    }

    fun dismissCleanup() {
        if (!isCleaningUp) cleanupScope = null
    }

    fun confirmCleanup() {
        val holders = cleanupProjects.map { it.holder }
        if (holders.isEmpty() || isCleaningUp) return
        val options = CleanupOptions(
            deleteBranches = cleanupDeleteBranches,
            stopGradleDaemons = cleanupStopGradleDaemons,
            stopEmulators = cleanupStopEmulators,
            deleteSimulators = cleanupDeleteSimulators,
            deleteDerivedData = cleanupDeleteDerivedData,
        )
        isCleaningUp = true
        viewModelScope.launch {
            try {
                cleanupSuccess = holders.mapNotNull { it.cleanUp(options) }.reduceOrNull { total, next -> total + next }
            } finally {
                isCleaningUp = false
                cleanupScope = null
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

    fun openSelectedInHerdr() {
        if (isCreateDialogOpen || TextInputFocus.isActive) return
        selectedWorktree?.let { it.holder.openInHerdr(it.worktree) }
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
private val AUTO_REFRESH_INTERVAL = 3.seconds
private val DEVICE_REFRESH_INTERVAL = 10.seconds
private val FOCUS_REFRESH_MIN_INTERVAL = 5.seconds
