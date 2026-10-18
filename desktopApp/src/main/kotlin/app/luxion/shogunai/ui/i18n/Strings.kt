package app.luxion.shogunai.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.usecase.GitFailureCause

interface Strings {
    val languageName: String

    val themeLight: String
    val themeDark: String
    val themeSystem: String

    val reload: String
    val cancel: String
    val delete: String
    val browse: String
    val expand: String
    val collapse: String

    val selectFolderDialogTitle: String
    val selectLocalFilesDialogTitle: String

    val projectsTitle: String
    val addProject: String
    val editProject: String
    val removeProject: String
    val removeProjectTitle: String
    fun removeProjectMessage(projectName: String): String
    val remove: String
    val emptyStateTitle: String
    val emptyStateMessage: String
    val collapseSidebar: String
    val expandSidebar: String

    val menuFile: String
    val menuView: String
    val menuHelp: String
    val menuWorktree: String
    val menuPreferences: String
    val menuQuit: String
    val menuToggleSidebar: String
    val menuAbout: String

    val preferencesTitle: String
    val appearanceLabel: String
    val languageLabel: String

    val aboutTitle: String
    val aboutTagline: String
    fun aboutVersion(version: String): String
    val aboutRepositoryLink: String

    val projectConfigTitle: String
    val identitySection: String
    val nameLabel: String
    val nameEmptyError: String
    val pathsSection: String
    val baseRepositoryPathLabel: String
    val baseRepositoryPathEmptyError: String
    val worktreesRootLabel: String
    val mainBranchLabel: String
    val mainBranchPlaceholder: String
    val localFilesSection: String
    val removeLocalFile: String
    val newLocalFileLabel: String
    val localFilesDescription: String
    val repositoryProvisioningSection: String
    val repositoryProvisioningDescription: String
    val repositoryProvisioningEmpty: String
    fun repositoryConfigUnreadable(path: String): String
    val moveToRepositoryConfig: String
    val pendingSave: String
    val add: String
    val agentSection: String
    val agentCommandLabel: String
    val useHeadroomLabel: String
    val headroomDescription: String
    val save: String
    val discardChangesTitle: String
    val discardChangesMessage: String
    val discard: String
    val keepEditing: String
    val generalSection: String
    val worktreeFilesSection: String
    val nameHelp: String
    val baseRepositoryPathHelp: String
    val worktreesRootHelp: String
    val mainBranchHelp: String
    fun detectedMainBranch(branch: String): String
    val unsavedChanges: String
    val menuSave: String
    val addProjectTitle: String
    val addProjectIntro: String
    val chooseRepositoryFolder: String
    val dropRepositoryHint: String
    fun notAGitRepository(path: String): String

    val closeError: String
    val closeMessage: String
    val technicalDetails: String
    val copyTechnicalDetails: String
    val tryAgain: String
    val openProjectSettings: String
    val openPreferences: String
    fun errorTexts(error: Throwable, gitCause: GitFailureCause): ErrorTexts
    val newWorktree: String
    val newBranchMode: String
    val existingBranchMode: String
    val taskIdLabel: String
    val taskIdHint: String
    val invalidTaskId: String
    val createWorktree: String
    val loadingBranches: String
    val noBranchesAvailable: String
    fun branchAlreadyHasWorktree(branch: String): String
    val selectBranchPlaceholder: String
    val otherBranchesGroup: String
    val worktreesTitle: String
    val viewsTitle: String
    val allWorktrees: String
    val activeWorktrees: String
    val groupAgentWorking: String
    val groupNeedsYou: String
    val groupInProgress: String
    val groupMerged: String
    val groupInactive: String
    val groupFolderMissing: String
    val groupOther: String
    val groupByState: String
    val groupByProject: String
    val readingWorktreeStatus: String
    val nothingActiveTitle: String
    val nothingActiveMessage: String
    val seeAllWorktrees: String
    val menuActive: String
    val menuAllWorktrees: String
    fun agentsRunning(names: List<String>): String
    val allProjects: String
    val projectLabel: String
    val filterWorktreesPlaceholder: String
    val noWorktreeMatches: String
    val clearFilter: String
    val menuFind: String
    val loadingWorktrees: String
    fun worktreeCount(count: Int): String
    val removeWorktreeTitle: String
    fun removeWorktreeMessage(path: String): String
    val forceRemoveTitle: String
    fun forceRemoveMessage(path: String): String
    fun alsoDeleteLocalBranch(branch: String): String
    val forceRemove: String
    val detachedHead: String
    fun uncommittedChanges(count: Int): String
    val upstream: String
    fun mergedInto(branch: String): String
    val statusUnavailable: String
    val folderMissing: String
    val mainWorktree: String
    val originShogunAi: String
    val originClaudeCode: String
    val originHerdr: String
    val originOther: String
    val inactive: String
    fun worktreesSummary(count: Int, totalSize: String): String
    fun cleanupCandidatesSummary(count: Int): String
    val locked: String
    val cleanUp: String
    val copyPath: String
    val statusTitle: String
    val agentsTitle: String
    val environmentTitle: String
    val devicesTitle: String
    val diskTitle: String
    val deleteWorktree: String
    val clearSelection: String
    val cleanUpTitle: String
    fun cleanUpMessage(count: Int): String
    val alsoDeleteLocalBranches: String
    fun worktreesCleanedUp(count: Int): String
    fun cleanupFailed(path: String, detail: String): String
    fun branchKept(branch: String): String
    val herdrWorkspaceWillClose: String
    val herdrCleanupNotice: String
    val openInHerdrMark: String
    fun herdrCloseFailed(path: String, detail: String): String
    val simulatorSection: String
    val simulatorSectionDescription: String
    val simulatorRuntimeLabel: String
    val simulatorDeviceLabel: String
    val simulatorAutomatic: String
    fun notOnThisMachine(name: String): String
    val simulatorTestsWired: String
    val simulatorTestsNotWired: String
    val copySnippet: String
    val installInitScript: String
    val installInitScriptTitle: String
    fun installInitScriptMessage(path: String): String
    val install: String
    val simulatorLabel: String
    val simulatorShutdown: String
    val simulatorBooting: String
    val simulatorBooted: String
    val startSimulator: String
    val stopSimulator: String
    val alsoDeleteSimulators: String
    fun orphanedSimulators(names: List<String>): String
    fun simulatorsDeleted(count: Int): String
    val emulatorSection: String
    val emulatorSectionDescription: String
    val emulatorAvdLabel: String
    val emulatorAutomaticAvd: String
    fun emulatorMissingAvd(name: String): String
    val emulatorNoWindowLabel: String
    val emulatorLabel: String
    val emulatorStopped: String
    val emulatorBooting: String
    val emulatorRunning: String
    val startEmulator: String
    val stopEmulator: String
    val alsoStopEmulators: String
    fun emulatorsStopped(count: Int): String
    val alsoStopIdleGradleDaemons: String
    val gradleDaemonsTitle: String
    fun gradleDaemonsSummary(count: Int, totalMemory: String): String
    val noGradleDaemons: String
    val gradleDaemonsUnavailable: String
    fun gradleDaemonLabel(version: String?): String
    val kotlinDaemonLabel: String
    val daemonBusy: String
    fun daemonIdle(since: String): String
    val daemonStateUnknown: String
    val daemonOwnerRemoved: String
    val daemonOwnerOther: String
    val stopDaemon: String
    val stopIdleDaemons: String
    val gradleBuildCacheOff: String
    fun gradleWorktreeSettings(daemonHeap: String?, idleTimeoutMinutes: Int?, kotlinDaemonHeap: String?): String
    fun daemonsStopped(stopped: Int, kept: Int, failed: Int): String
    val justNow: String

    val xcodeDerivedDataTitle: String
    val xcodeDerivedDataUnavailable: String
    val noDerivedDataFolders: String
    fun derivedDataSummary(count: Int, totalSize: String): String
    fun derivedDataOrphans(count: Int, size: String): String
    val deleteOrphans: String
    fun derivedDataOnUnavailableVolumes(count: Int): String
    fun derivedDataCompilationCache(size: String): String
    fun derivedDataDeleted(count: Int, freed: String): String
    val machineTitle: String
    val androidEmulatorsTitle: String
    val iosSimulatorsTitle: String
    fun runningCount(count: Int): String
    fun bootedCount(count: Int): String
    val ownerOutsideProjects: String
    val ownerLoading: String
    val notOwnedByWorktree: String
    val goToWorktree: String
    val noMachineEmulators: String
    val unownedEmulatorsTitle: String
    val noMachineSimulators: String
    fun simulatorOrphansTitle(count: Int): String
    val confirmDeleteOrphansTitle: String
    fun confirmDeleteSimulatorOrphans(count: Int): String
    fun confirmDeleteDerivedDataOrphans(count: Int, size: String): String
    val noDerivedData: String
    val derivedDataKept: String
    val gradleChipLabel: String
    fun openGradleDaemons(project: String): String
    val alsoDeleteDerivedData: String
    fun orphanedDerivedData(count: Int, size: String): String
    fun minutesAgo(count: Long): String
    fun hoursAgo(count: Long): String
    fun daysAgo(count: Long): String
    fun monthsAgo(count: Long): String
    fun yearsAgo(count: Long): String
    val copied: String
    val copyCommand: String
    val copyLaunchCommand: String
    fun worktreeCreated(folder: String): String
    val worktreesRepairedAfterMove: String
    val openInHerdr: String
    fun openedInHerdr(name: String): String
    fun focusedInHerdr(name: String): String

    val repositoryConfigSection: String
    val repositoryConfigSharedNote: String
    val repositoryConfigProvidersTitle: String
    val repositoryConfigEnvironmentTitle: String
    val providerGradle: String
    val providerEmulator: String
    val providerIosSimulator: String
    val providerXcode: String
    val gradleDaemonMaxHeapLabel: String
    val gradleIdleTimeoutLabel: String
    val gradleKotlinDaemonMaxHeapLabel: String
    val preferredAvdLabel: String
    val preferredDeviceLabel: String
    val preferredRuntimeLabel: String
    val xcodeCompilationCacheLabel: String
    val environmentVariableNameLabel: String
    val environmentVariableTemplateLabel: String
    val addVariable: String
    val removeVariable: String
    val environmentEmpty: String
    val envFileLabel: String
    val envFileHelp: String
    val templateHelpTitle: String
    val templateHelp: List<TemplateHelp>
    val memorySizeInvalid: String
    val minutesInvalid: String
    val variableNameInvalid: String
    val variableNameDuplicate: String
    val templateUnclosed: String
    fun templateUnknown(placeholder: String): String
    val envFileOutsideWorktree: String
    val repositoryConfigUnreadableExplanation: String
    val reloadRepositoryConfig: String
    val machineOnlyLabel: String
    val repositoryPreviewTitle: String
    val repositoryPreviewMessage: String
    val repositoryPreviewConfirm: String
    fun repositoryConfigChanged(path: String): String
    val menuCommandPalette: String
    val paletteHint: String
    val paletteNoResults: String
    val paletteKindView: String
    val paletteKindProject: String
    val paletteKindWorktree: String
    val paletteKindAction: String
    val paletteViewActive: String
    val paletteViewAllWorktrees: String
    val paletteViewPreferences: String
    val paletteCleanUp: String
    val paletteCopyLaunchCommandHint: String
    fun paletteNewWorktreeIn(project: String): String
    fun paletteSettingsOf(project: String): String
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

data class TemplateHelp(val template: String, val description: String)

fun Strings.repositoryConfigIssue(issue: RepositoryConfigIssue): String = when (issue) {
    RepositoryConfigIssue.InvalidMemorySize -> memorySizeInvalid
    RepositoryConfigIssue.InvalidMinutes -> minutesInvalid
    RepositoryConfigIssue.InvalidVariableName -> variableNameInvalid
    RepositoryConfigIssue.DuplicateVariableName -> variableNameDuplicate
    RepositoryConfigIssue.UnclosedPlaceholder -> templateUnclosed
    is RepositoryConfigIssue.UnknownPlaceholder -> templateUnknown(issue.placeholder)
    RepositoryConfigIssue.EnvFileOutsideWorktree -> envFileOutsideWorktree
}

fun AppLanguage.strings(): Strings = when (this) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SPANISH -> SpanishStrings
}
