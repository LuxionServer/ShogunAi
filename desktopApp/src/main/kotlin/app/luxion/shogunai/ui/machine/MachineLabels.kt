package app.luxion.shogunai.ui.machine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Dns
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

data class DeviceLabel(val data: String?, val note: String?)

fun emulatorDeviceLabel(entry: MachineEmulator, project: Project?, strings: Strings): DeviceLabel =
    (entry.emulator?.serial ?: project?.config?.emulator?.avd?.takeIf { it.isNotBlank() })
        ?.let { DeviceLabel(data = it, note = null) }
        ?: DeviceLabel(data = null, note = strings.automaticAvd)

fun simulatorDeviceLabel(entry: MachineSimulator, project: Project?, strings: Strings): DeviceLabel =
    entry.simulator?.name?.let { DeviceLabel(data = it, note = null) }
        ?: DeviceLabel(data = project?.config?.simulator?.device?.takeIf { it.isNotBlank() }, note = strings.simulatorNotCreated)

fun MachineResource.title(strings: Strings): String = when (this) {
    MachineResource.GRADLE_DAEMONS -> strings.gradleDaemonsTitle
    MachineResource.ANDROID_EMULATORS -> strings.androidEmulatorsTitle
    MachineResource.IOS_SIMULATORS -> strings.iosSimulatorsTitle
    MachineResource.XCODE_DERIVED_DATA -> strings.xcodeDerivedDataTitle
    MachineResource.MAVEN_DAEMONS -> strings.mavenDaemonsTitle
    MachineResource.COMPOSE_PROJECTS -> strings.composeProjectsTitle
    MachineResource.DESKTOP_TURN -> strings.desktopTurnTitle
}

val MachineResource.icon: ImageVector
    get() = when (this) {
        MachineResource.GRADLE_DAEMONS -> Icons.Default.Memory
        MachineResource.ANDROID_EMULATORS -> Icons.Default.PhoneAndroid
        MachineResource.IOS_SIMULATORS -> Icons.Default.PhoneIphone
        MachineResource.XCODE_DERIVED_DATA -> Icons.Default.Storage
        MachineResource.MAVEN_DAEMONS -> Icons.Default.Memory
        MachineResource.COMPOSE_PROJECTS -> Icons.Default.Dns
        MachineResource.DESKTOP_TURN -> Icons.Default.DesktopMac
    }

fun MachineSummary.text(resource: MachineResource, strings: Strings): String {
    val base = when (resource) {
        MachineResource.GRADLE_DAEMONS -> "$count · ${formatSize(bytes ?: 0)}"
        MachineResource.ANDROID_EMULATORS -> strings.runningCount(count)
        MachineResource.IOS_SIMULATORS -> strings.bootedCount(count)
        MachineResource.MAVEN_DAEMONS -> "$count · ${formatSize(bytes ?: 0)}"
        MachineResource.COMPOSE_PROJECTS -> strings.composeProjectsSummary(count)
        MachineResource.DESKTOP_TURN -> if (holder == null && count == 0) strings.desktopTurnFree else strings.desktopTurnSummary(holder, count)
        MachineResource.XCODE_DERIVED_DATA -> when {
            calculating -> "$count · ${strings.calculating}"
            quotaBytes != null -> strings.derivedDataQuotaUsage(formatSize(bytes ?: 0), formatSize(quotaBytes), quotaAutomatic)
            else -> formatSize(bytes ?: 0)
        }
    }
    val pool = poolInUse?.takeIf { poolKindOf(resource) != null }?.let { strings.poolSummary(it, poolLimit) }
    return if (pool == null) base else "$base · $pool"
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
