package app.luxion.shogunai.ui.machine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.formatSize

fun emulatorDeviceLabel(entry: MachineEmulator, project: Project?, strings: Strings): String =
    entry.emulator?.serial
        ?: project?.config?.emulator?.avd?.takeIf { it.isNotBlank() }
        ?: strings.automaticAvd

fun simulatorDeviceLabel(entry: MachineSimulator, project: Project?, strings: Strings): String =
    entry.simulator?.name
        ?: project?.config?.simulator?.device?.takeIf { it.isNotBlank() }?.let(strings::simulatorNotCreatedFor)
        ?: strings.simulatorNotCreated

fun MachineResource.title(strings: Strings): String = when (this) {
    MachineResource.GRADLE_DAEMONS -> strings.gradleDaemonsTitle
    MachineResource.ANDROID_EMULATORS -> strings.androidEmulatorsTitle
    MachineResource.IOS_SIMULATORS -> strings.iosSimulatorsTitle
    MachineResource.XCODE_DERIVED_DATA -> strings.xcodeDerivedDataTitle
}

val MachineResource.icon: ImageVector
    get() = when (this) {
        MachineResource.GRADLE_DAEMONS -> Icons.Default.Memory
        MachineResource.ANDROID_EMULATORS -> Icons.Default.PhoneAndroid
        MachineResource.IOS_SIMULATORS -> Icons.Default.PhoneIphone
        MachineResource.XCODE_DERIVED_DATA -> Icons.Default.Storage
    }

fun MachineSummary.text(resource: MachineResource, strings: Strings): String = when (resource) {
    MachineResource.GRADLE_DAEMONS -> "$count · ${formatSize(bytes ?: 0)}"
    MachineResource.ANDROID_EMULATORS -> strings.runningCount(count)
    MachineResource.IOS_SIMULATORS -> strings.bootedCount(count)
    MachineResource.XCODE_DERIVED_DATA -> if (calculating) "$count · ${strings.calculating}" else formatSize(bytes ?: 0)
}

fun formatMemorySetting(size: String): String {
    val unit = when (size.last().lowercaseChar()) {
        'g' -> "GB"
        'm' -> "MB"
        else -> "KB"
    }
    return "${size.dropLast(1)} $unit"
}

fun GradleProvider.settingsText(strings: Strings): String? =
    takeIf { it.hasSettings }?.let {
        strings.gradleWorktreeSettings(
            daemonMaxHeap?.let(::formatMemorySetting),
            idleTimeoutMinutes,
            kotlinDaemonMaxHeap?.let(::formatMemorySetting),
        )
    }

fun ProjectOwner.label(strings: Strings, projectName: (String) -> String): String = when (this) {
    is ProjectOwner.Owned -> "${projectName(projectId)} · ${worktreePath.substringAfterLast('/')}"
    ProjectOwner.RemovedWorktree -> strings.daemonOwnerRemoved
    ProjectOwner.Outside -> strings.ownerOutsideProjects
    ProjectOwner.Loading -> strings.ownerLoading
}
