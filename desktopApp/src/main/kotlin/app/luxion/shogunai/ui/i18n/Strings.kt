package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import androidx.compose.runtime.staticCompositionLocalOf
import app.luxion.shogunai.domain.model.InstallChannel
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.usecase.HookEntry
import app.luxion.shogunai.domain.usecase.HookReplacement
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.CommandLineToolInstallation
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.ui.shell.HooksAction
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
    val nameLabel: String
    val nameEmptyError: String
    val baseRepositoryPathLabel: String
    val baseRepositoryPathEmptyError: String
    val baseRepositoryNotTopLevelError: String
    fun baseRepositoryAlreadyAdded(projectName: String): String
    val worktreesRootEmptyError: String
    val agentCommandEmptyError: String
    val discardRepositoryEditsTitle: String
    fun discardRepositoryEditsMessage(path: String): String
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
    val agentNameLabel: String
    fun duplicateAgentNameError(name: String): String
    val addAgent: String
    val removeAgent: String
    val makeDefaultAgent: String
    val defaultAgentMark: String
    val moveAgentUp: String
    val moveAgentDown: String
    val worktreeAgentLabel: String
    val chooseAgent: String
    fun launchWithAgent(action: String, agent: String): String
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
    fun projectSettingsTitle(name: String): String
    val linkedPattern: String
    fun postCreationCommandsNote(count: Int): String
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
    val startFromLabel: String
    fun startFromMainBranch(ref: String): String
    fun startFromCurrentBranch(branch: String?): String
    fun remoteBranchLabel(branch: String): String
    val fetchFirstLabel: String
    val createWithoutFetching: String
    val otherBranchesGroup: String
    val worktreesTitle: String
    val viewsTitle: String
    val allWorktrees: String
    val activeWorktrees: String
    val groupAgentWorking: String
    val groupNeedsYou: String
    val groupNew: String
    val groupStopped: String
    val groupMerged: String
    val groupInactive: String
    val groupFolderMissing: String
    val groupOther: String
    val groupAgentWorkingHint: String
    val groupAgentWorkingCriterion: String
    val groupNeedsYouHint: String
    val groupNeedsYouCriterion: String
    val groupNewHint: String
    val groupNewCriterion: String
    val groupStoppedHint: String
    val groupStoppedCriterion: String
    val groupMergedHint: String
    val groupMergedCriterion: String
    fun groupInactiveHint(days: Int): String
    fun groupInactiveCriterion(days: Int): String
    val groupFolderMissingHint: String
    val groupFolderMissingCriterion: String
    val groupOtherHint: String
    val groupOtherCriterion: String
    val groupByState: String
    val groupByProject: String
    val readingWorktreeStatus: String
    val nothingActiveTitle: String
    val nothingActiveMessage: String
    val seeAllWorktrees: String
    val menuActive: String
    val menuAllWorktrees: String
    fun agentsRunning(names: List<String>): String
    fun agentLabel(name: String, state: AgentState?): String
    val claudeCodeLabel: String
    fun claudeHooksStatus(state: HooksInstallState): String
    val claudeHooksInstall: String
    val claudeHooksUpdate: String
    val claudeHooksRemove: String
    val claudeHooksCancel: String
    val claudeHooksConfirm: String
    val claudeHooksNeedsTool: String
    val commandLineToolLabel: String
    fun commandLineToolStatus(status: CommandLineToolStatus): String
    val commandLineToolNeededByHooks: String
    val commandLineToolInstall: String
    val commandLineToolRepair: String
    val commandLineToolRemove: String
    fun commandLineToolInstalled(installation: CommandLineToolInstallation): String
    fun commandLineToolPathHint(status: CommandLineToolStatus, os: HostOs): String
    fun commandLineToolManualSteps(os: HostOs): String
    val claudeHooksNoticeToolMessage: String
    fun claudeHooksDialogTitle(action: HooksAction): String
    fun claudeHooksChangesIn(path: String): String
    fun claudeHooksAdded(entry: HookEntry): String
    fun claudeHooksReplaced(replacement: HookReplacement): String
    fun claudeHooksRemoved(entry: HookEntry): String
    val claudeHooksNoticeMessage: String
    val claudeHooksNoticeDismiss: String
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
    val notStarted: String
    val mergedLabel: String
    fun commitAgo(relative: String): String
    fun createdAgo(relative: String): String
    fun agentStateName(state: AgentState?): String
    val sortLabel: String
    val sortByName: String
    val sortByLastActivity: String
    val createdTitle: String
    val inactivityThresholdLabel: String
    val inactivityThresholdHint: String
    val inactivityThresholdInvalid: String
    val statusUnavailable: String
    val mainWorktree: String
    val originShogunAi: String
    val originClaudeCode: String
    val originHerdr: String
    val originOther: String
    fun worktreesSummary(count: Int, totalSize: String): String
    fun cleanupCandidatesSummary(count: Int): String
    val locked: String
    val cleanUp: String
    val copyPath: String
    val statusTitle: String
    val agentsTitle: String
    val environmentTitle: String
    val provisioningTitle: String
    val provisioningChecking: String
    val provisioningUnavailable: String
    val provisioningUpToDate: String
    val provisionAction: String
    val copyAsEnv: String
    fun provisionDriftFile(path: String, state: ProvisionDriftState): String
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
    fun worktreeRemovedBranchKept(branch: String): String
    fun worktreeRemovedBranchKeptFailed(branch: String, reason: String): String
    val deleteBranchAnyway: String
    val deleteBranchAnywayTitle: String
    fun deleteBranchAnywayMessage(branch: String): String
    fun branchDeleted(branch: String): String
    val herdrWorkspaceWillClose: String
    val herdrCleanupNotice: String
    fun keptForRunningAgent(name: String, agents: List<String>): String
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
    val uninstallInitScript: String
    val uninstallInitScriptTitle: String
    fun uninstallInitScriptMessage(path: String): String
    val uninstall: String
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
    val stopDaemon: String
    val stopIdleDaemons: String
    val gradleBuildCacheOff: String
    fun gradleWorktreeSettings(daemonHeap: String?, idleTimeoutMinutes: Int?, kotlinDaemonHeap: String?): String
    fun daemonsStopped(stopped: Int, kept: Int, failed: Int): String
    val justNow: String

    val xcodeDerivedDataTitle: String
    val xcodeDerivedDataUnavailable: String
    val calculating: String
    fun derivedDataSummary(count: Int, totalSize: String): String
    fun derivedDataOrphans(count: Int, size: String): String
    val deleteOrphans: String
    fun derivedDataOnUnavailableVolumes(count: Int): String
    fun derivedDataCompilationCache(size: String): String
    fun derivedDataDeleted(count: Int, freed: String): String
    fun derivedDataQuotaUsage(usage: String, quota: String, automatic: Boolean): String
    val derivedDataQuotaUnknown: String
    fun derivedDataQuotaExceeded(inUse: List<String>): String
    fun derivedDataReleased(count: Int, freed: String): String
    val machineTitle: String
    val resourcesLabel: String
    fun profileName(profile: CapacityProfile): String
    fun resourceLimitsSummary(limits: ResourceLimits, includeSimulators: Boolean): String
    fun resourceKindLabel(kind: ResourceKind): String
    val noLimit: String
    val invalidLimit: String
    val derivedDataQuotaLabel: String
    val noQuota: String
    fun quotaModeName(mode: DerivedDataQuotaMode): String
    val automaticQuotaHint: String
    val gigabytesUnit: String
    val invalidQuota: String
    val leaseDurationLabel: String
    val minutesUnit: String
    val invalidLeaseDuration: String
    val leaseDurationHint: String
    val desktopTurnDurationLabel: String
    val desktopTurnDurationHint: String
    val desktopTurnTitle: String
    fun desktopTurnSummary(holder: String?, waiting: Int): String
    val desktopTurnExplanation: String
    val desktopTurnFree: String
    fun desktopTurnHeld(since: String, until: String): String
    val desktopTurnWaitingTitle: String
    fun desktopTurnWaitingSince(since: String): String
    val releaseDesktopTurn: String
    val confirmReleaseDesktopTurnTitle: String
    fun confirmReleaseDesktopTurnMessage(holder: String): String
    val desktopHeldLabel: String
    fun desktopHeldUntil(until: String): String
    fun desktopWaitingLabel(position: Int): String
    val desktopTurnDetailTitle: String
    fun desktopTurnDetailWaiting(position: Int, since: String, holder: String?): String
    val headlessAppRunningLabel: String
    val headlessAppDetailTitle: String
    fun headlessAppRunning(pid: Long?): String
    val headlessAppStopped: String
    fun headlessAppLog(path: String): String
    val stopHeadlessApp: String
    fun headlessStopFailed(path: String, detail: String): String
    fun releasedFor(worktree: String, time: String): String
    val notAcquired: String
    fun poolUsage(inUse: Int, limit: Int?, profile: String): String
    val overLimit: String
    val changeInPreferences: String
    fun releasedNotice(kind: ResourceKind, released: String, forWorktree: String): String
    fun trimmedNotice(count: Int, limit: Int?): String
    fun poolSummary(inUse: Int, limit: Int?): String
    val androidEmulatorsTitle: String
    val iosSimulatorsTitle: String
    fun runningCount(count: Int): String
    fun bootedCount(count: Int): String
    val ownerOutsideProjects: String
    val ownerLoading: String
    val notOwnedByWorktree: String
    val machineColumnDevice: String
    val machineColumnWorktree: String
    val machineColumnState: String
    val automaticAvd: String
    val simulatorNotCreated: String
    fun simulatorNotCreatedFor(device: String): String
    val noMachineEmulators: String
    val unownedEmulatorsTitle: String
    val noMachineSimulators: String
    fun simulatorOrphansTitle(count: Int): String
    val confirmDeleteOrphansTitle: String
    fun confirmDeleteSimulatorOrphans(count: Int): String
    fun confirmDeleteDerivedDataOrphans(count: Int, size: String?): String
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
    val menuCopyDiagnosticReport: String
    val secondaryWindowError: String
    val menuCheckForUpdates: String
    fun updateAvailableMessage(newVersion: String, runningVersion: String): String
    fun updateAvailableDialogMessage(newVersion: String, runningVersion: String): String
    val updateWhatsNew: String
    val updateSkipVersion: String
    val updateCopy: String
    val updateDownload: String
    val updateOpenReleasePage: String
    fun updateCommandReminder(channel: InstallChannel, command: String): String
    val updateDownloadReminder: String
    fun updateInstallCommandReminder(command: String): String
    val updateReleasePageReminder: String
    val updateDialogTitle: String
    val updateChecking: String
    fun updateUpToDateMessage(version: String): String
    val updateCheckAutomaticallyLabel: String
    val copied: String
    val close: String
    val worktreeCreatedWithError: String
    val copyLaunchCommand: String
    fun worktreeCreated(folder: String): String
    fun worktreeCreatedCommandsSkipped(folder: String): String
    val postCreateApprovalTitle: String
    val postCreateApprovalExplanation: String
    val runAndRemember: String
    val skipCommands: String
    val postCreateCommandsTitle: String
    val postCreateApproved: String
    val postCreateNotApproved: String
    val approveCommands: String
    val revokeApproval: String
    val variableNameReserved: String
    val approvalVariablesTitle: String
    fun approvalEnvFile(path: String): String
    fun variablesAwaitingApproval(names: List<String>): String
    val templateInvalidCharacters: String
    val envFileInvalidCharacters: String
    val worktreesRepairedAfterMove: String
    val openInHerdr: String
    fun openedInHerdr(name: String): String
    fun focusedInHerdr(name: String): String
    fun agentStartedInHerdr(name: String): String

    val toolsLabel: String
    val terminalToolLabel: String
    val editorToolLabel: String
    val noTerminalFound: String
    val noEditorFound: String
    fun openInTool(name: String): String
    val claudeDesktopName: String
    val openFolder: String
    val chooseTerminalTitle: String
    val chooseTerminalMessage: String
    val projectEditorLabel: String
    fun defaultEditorOption(name: String?): String
    fun editorNotFoundOption(name: String): String
    fun editorOverrideMissing(name: String): String

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
    val providerMaven: String
    val providerDockerCompose: String
    val mavenMaxHeapLabel: String
    val composePortVariablesLabel: String
    val composePortVariablesHelp: String
    val stackSuggestionsTitle: String
    fun stackSuggestion(provider: String, evidence: String): String
    val turnOnProvider: String
    val linkLocalEntry: String
    val localPatternLabel: String
    fun skippedSymbolicLinks(count: Int): String
    val localPatternMustBeRelative: String
    val mobileSecretsTitle: String
    fun mobileSecretKind(kind: MobileSecretKind): String
    val linkFromRepositoryConfig: String
    fun keystoreOutsideRepository(file: String, key: String, value: String): String
    val mavenDaemonsTitle: String
    fun mavenDaemonsSummary(count: Int, totalMemory: String): String
    val noMavenDaemons: String
    val mavenDaemonsUnavailable: String
    val mavenDaemonsNotStopped: String
    val composeProjectsTitle: String
    fun composeProjectsSummary(count: Int): String
    val noComposeProjects: String
    fun composeProjectsUnavailable(reason: String?): String
    val composeOwnerRemoved: String
    val takeDownRemovedComposeProjects: String
    val composeVolumesKept: String
    fun composeProjectsTakenDown(count: Int): String
    fun composeProjectDownFailed(name: String, detail: String): String
    val alsoTakeDownComposeProjects: String
    fun cleanupComposeKept(reason: String?): String
    val composeSectionTitle: String
    val composeNotStarted: String
    val startComposeServices: String
    val buildComposeServices: String
    val takeDownComposeServices: String
    val composeDockerUnavailable: String
    val composeLeftoversTitle: String
    fun composeResourceKind(kind: ComposeResourceKind): String
    val removeComposeLeftovers: String
    val removeComposeLeftoversWithVolumes: String
    val confirmRemoveComposeVolumesTitle: String
    fun confirmRemoveComposeVolumes(count: Int): String
    val removeVolumes: String
    fun composeResourcesRemoved(count: Int): String
    fun composeResourceRemoveFailed(name: String, detail: String): String
    val alsoRemoveComposeVolumes: String
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
    val paletteCopyLaunchCommandHint: String
    val paletteKindView: String
    val paletteKindProject: String
    val paletteKindWorktree: String
    val paletteKindAction: String
    val paletteViewActive: String
    val paletteViewAllWorktrees: String
    val paletteViewPreferences: String
    val paletteCleanUp: String
    fun paletteNewWorktreeIn(project: String): String
    fun paletteSettingsOf(project: String): String
    val trayShowApp: String
    val trayNewWorktree: String
    val trayRecentProjects: String
    val trayRecentWorktrees: String
    val trayShowInApp: String
    val showIconInMenuBar: String
    val showIconInTray: String
    val keepRunningWhenClosed: String
    val groupByChange: String
    val ungrouped: String
    val newChange: String
    val newChangeRepositories: String
    val createChange: String
    val openAllInHerdr: String
    val addRepositoryToGroup: String
    val cleanUpGroup: String
    val cleanUpGroupTitle: String
    val openMultirepoAgent: String
    val multirepoAgentOpened: String
    val multirepoAgentFocused: String
    val multirepoAgentStarted: String
    val groupRepositoryCreated: String
    val groupRepositoryExisting: String
    val addMultirepo: String
    val addMultirepoExplanation: String
    val multirepoFolderPlaceholder: String
    val chooseFolder: String
    val multirepoRepositoriesTitle: String
    val noRepositoriesFound: String
    val multirepoAliasLabel: String
    val multirepoAliasInvalid: String
    val multirepoAliasDuplicated: String
    val alreadyAProject: String
    val addRepositoryFromElsewhere: String
    val detectedGroupsTitle: String
    val noGroupsDetected: String
    val multirepoSettingsTitle: String
    val multirepoNotFound: String
    val workspaceRepositoryTitle: String
    val workspaceRepositoryHint: String
    val workspaceDetectedMark: String
    val declareWorkspace: String
    val workspaceAliasLabel: String
    val turnOffWorkspace: String
    val returnWorkspaceToDetection: String
    val sharedVariablesTitle: String
    val sharedVariablesHint: String
    val sharedVariablesEmpty: String
    val sharedVariableOwnPort: String
    fun sharedVariableUnknownRepository(alias: String): String
    fun sharedVariablesUnrepresentable(problem: String): String
    val workspaceTurnedOff: String
    val defaultRepositoriesTitle: String
    val defaultRepositoriesHint: String
    val multirepoWorktreesRootTitle: String
    val multirepoWorktreesRootHint: String
    val multirepoSharedVariablesTitle: String
    val multirepoAgentTitle: String
    val multirepoAgentHint: String
    val deleteMultirepo: String
    val deleteMultirepoHint: String
    val deleteMultirepoMessage: String
    val rename: String
    val addRepository: String
    val repositoryPathLabel: String
    val multirepoNameTitle: String
    val multirepoNameHint: String
    val moveUp: String
    val moveDown: String
    val sortAlphabetically: String
    val multirepoNeedsReview: String
    val multirepoNeedsReviewExplanation: String
    val trustMultirepo: String
    val declaredAgentIgnored: String
    val existingDeclarationTitle: String
    fun groupKeptNotMerged(alias: String): String
    fun groupKeptUncommitted(alias: String): String
    fun groupKeptStatusUnavailable(alias: String): String
    fun groupKeptAgentRunning(alias: String, agents: List<String>): String
    fun groupRepositoryFailed(reason: String): String
    fun repositoryAddedToGroup(alias: String, title: String): String
    fun changeCreated(title: String, aliases: List<String>): String
    fun multirepoInsideRepository(repository: String): String
    fun useWorktreesFolder(folder: String): String
    fun notARepository(path: String): String
    fun folderIsMultirepoWorkspace(path: String, multirepo: String): String
    fun deleteMultirepoTitle(name: String): String
    fun outsideMultirepoParent(parent: String): String
    val tourMenuItem: String
    val tourTakeTour: String
    val tourDialogTitle: String
    fun tourWelcomeMessage(folder: String): String
    val tourStart: String
    val tourToolMessage: String
    val tourSkip: String
    val tourContinue: String
    val tourCreating: String
    val tourRetry: String
    val tourOpenAgentMessage: String
    val tourClaudeDesktopHint: String
    val tourTrustHint: String
    val tourAgentsNeeded: String
    val tourLoadingWorktrees: String
    val tourLater: String
    val tourExistingMessage: String
    val tourRestart: String
    val tourOpenExisting: String
    val tourNoticeMessage: String
    val tourBannerMessage: String
    val tourOpenAgent: String
    val tourEnd: String
    val tourEndTitle: String
    fun tourEndMessage(folder: String): String
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

data class TemplateHelp(val template: String, val description: String)

fun Strings.repositoryConfigIssue(issue: RepositoryConfigIssue): String = when (issue) {
    RepositoryConfigIssue.InvalidMemorySize -> memorySizeInvalid
    RepositoryConfigIssue.InvalidMinutes -> minutesInvalid
    RepositoryConfigIssue.InvalidVariableName -> variableNameInvalid
    RepositoryConfigIssue.ReservedVariableName -> variableNameReserved
    RepositoryConfigIssue.InvalidVariableValue -> templateInvalidCharacters
    RepositoryConfigIssue.DuplicateVariableName -> variableNameDuplicate
    RepositoryConfigIssue.UnclosedPlaceholder -> templateUnclosed
    is RepositoryConfigIssue.UnknownPlaceholder -> templateUnknown(issue.placeholder)
    RepositoryConfigIssue.OwnPortInSharedVariable -> sharedVariableOwnPort
    is RepositoryConfigIssue.UnknownMultirepoRepository -> sharedVariableUnknownRepository(issue.alias)
    RepositoryConfigIssue.EnvFileOutsideWorktree -> envFileOutsideWorktree
    RepositoryConfigIssue.EnvFileInvalidCharacters -> envFileInvalidCharacters
}

fun AppLanguage.strings(): Strings = when (this) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SPANISH -> SpanishStrings
}

internal fun InstallChannel.installerName(): String = when (this) {
    InstallChannel.HOMEBREW -> "Homebrew"
    InstallChannel.SCOOP -> "Scoop"
    InstallChannel.WINGET -> "winget"
    else -> name
}
