package app.luxion.shogunai.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.WorktreeError

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
    fun saveFailed(detail: String?): String
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

    val closeError: String
    val closeMessage: String
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

    fun baseRepositoryNotFound(path: String): String
    fun worktreeAlreadyExists(path: String): String
    fun invalidTaskIdError(rawInput: String): String
    fun secretFilesNotFound(files: List<String>, basePath: String): String
    fun invalidRepositoryConfig(path: String, detail: String?): String
    fun repositoryNotFound(path: String): String
    fun notAWorktree(path: String): String
    fun gitCommandFailed(command: List<String>, exitCode: Int): String
    fun secretCopyFailed(detail: String?): String
    fun clipboardWriteFailed(detail: String?): String
    val gitLfsNotFound: String
    fun branchNotFound(branch: String): String
    fun branchAlreadyCheckedOut(branch: String, path: String?): String
    fun commandCouldNotStart(command: List<String>): String
    fun processListingFailed(command: List<String>, exitCode: Int): String
    fun noFreePort(from: Int, to: Int): String
    fun noEmulatorPort(from: Int, to: Int): String
    fun androidSdkNotFound(checked: List<String>): String
    val noAvdAvailable: String
    val herdrNotInstalled: String
    fun herdrCommandFailed(detail: String): String
    fun emulatorBootTimeout(serial: String, seconds: Long): String
    val emulatorProviderNotDeclared: String
    val noIosRuntime: String
    fun simulatorBootTimeout(name: String, seconds: Long): String
    val iosSimulatorProviderNotDeclared: String
    val iosSimulatorsNotSupported: String
    fun appleToolFailed(command: List<String>, exitCode: Int): String
    fun androidToolFailed(command: List<String>, exitCode: Int): String
    fun environmentFileTracked(path: String): String
    fun environmentFileWriteFailed(path: String, detail: String?): String
    fun unexpectedError(detail: String?): String
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

fun AppLanguage.strings(): Strings = when (this) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.SPANISH -> SpanishStrings
}

fun Strings.worktreeError(error: Throwable): String = when (error) {
    is WorktreeError -> worktreeErrorMessage(error)
    is CommandExecutionException -> commandCouldNotStart(error.command)
    else -> unexpectedError(error.message)
}

private fun Strings.worktreeErrorMessage(error: WorktreeError): String = when (error) {
    is WorktreeError.BaseRepositoryNotFound -> baseRepositoryNotFound(error.path)
    is WorktreeError.WorktreeAlreadyExists -> worktreeAlreadyExists(error.path)
    is WorktreeError.InvalidTaskId -> invalidTaskIdError(error.rawInput)
    is WorktreeError.SecretFileNotFound -> secretFilesNotFound(error.files, error.basePath)
    is WorktreeError.InvalidRepositoryConfig -> invalidRepositoryConfig(error.path, error.detail)
    is WorktreeError.RepositoryNotFound -> repositoryNotFound(error.path)
    is WorktreeError.NotAWorktree -> notAWorktree(error.path)
    is WorktreeError.GitCommandFailed -> gitCommandFailed(error.command, error.exitCode) +
        if (error.errorOutput.isNotBlank()) "\n${error.errorOutput}" else ""
    is WorktreeError.SecretCopyFailed -> secretCopyFailed(error.cause?.message)
    is WorktreeError.ClipboardWriteFailed -> clipboardWriteFailed(error.cause?.message)
    WorktreeError.GitLfsNotFound -> gitLfsNotFound
    is WorktreeError.BranchNotFound -> branchNotFound(error.branch)
    is WorktreeError.BranchAlreadyCheckedOut -> branchAlreadyCheckedOut(error.branch, error.path)
    is WorktreeError.ProcessListingFailed -> processListingFailed(error.command, error.exitCode)
    is WorktreeError.NoFreePort -> noFreePort(error.from, error.to)
    is WorktreeError.NoEmulatorPort -> noEmulatorPort(error.from, error.to)
    is WorktreeError.AndroidSdkNotFound -> androidSdkNotFound(error.checked)
    WorktreeError.NoAvdAvailable -> noAvdAvailable
    WorktreeError.HerdrNotInstalled -> herdrNotInstalled
    is WorktreeError.HerdrCommandFailed -> herdrCommandFailed(error.detail)
    is WorktreeError.EmulatorBootTimeout -> emulatorBootTimeout(error.serial, error.seconds)
    WorktreeError.EmulatorProviderNotDeclared -> emulatorProviderNotDeclared
    WorktreeError.NoIosRuntime -> noIosRuntime
    is WorktreeError.SimulatorBootTimeout -> simulatorBootTimeout(error.name, error.seconds)
    WorktreeError.IosSimulatorProviderNotDeclared -> iosSimulatorProviderNotDeclared
    WorktreeError.IosSimulatorsNotSupported -> iosSimulatorsNotSupported
    is WorktreeError.AppleToolFailed -> appleToolFailed(error.command, error.exitCode) +
        if (error.output.isNotBlank()) "\n${error.output}" else ""
    is WorktreeError.AndroidToolFailed -> androidToolFailed(error.command, error.exitCode) +
        if (error.output.isNotBlank()) "\n${error.output}" else ""
    is WorktreeError.EnvironmentFileTracked -> environmentFileTracked(error.path)
    is WorktreeError.EnvironmentFileWriteFailed -> environmentFileWriteFailed(error.path, error.cause?.message)
}
