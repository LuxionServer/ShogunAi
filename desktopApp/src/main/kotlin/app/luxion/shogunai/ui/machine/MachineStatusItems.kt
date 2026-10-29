package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.worktree.formatSize

data class MachineStatusItem(
    val resource: MachineResource,
    val name: String,
    val figure: String?,
    val tooltip: String,
    val attention: Status?,
)

val MachineStatusOrder: List<MachineResource> = listOf(
    MachineResource.DESKTOP_TURN,
    MachineResource.GRADLE_DAEMONS,
    MachineResource.ANDROID_EMULATORS,
    MachineResource.IOS_SIMULATORS,
    MachineResource.XCODE_DERIVED_DATA,
    MachineResource.MAVEN_DAEMONS,
    MachineResource.COMPOSE_PROJECTS,
)

fun machineStatusItems(
    resources: Collection<MachineResource>,
    summaries: Map<MachineResource, MachineSummary>,
    failures: Map<MachineResource, Throwable>,
    strings: Strings,
): List<MachineStatusItem> =
    MachineStatusOrder.filter { it in resources }.map { resource ->
        val summary = summaries[resource]
        val failure = failures[resource]
        val warning = summary?.warning(resource, strings)
        val details = listOfNotNull(
            summary?.takeUnless { it.calculating }?.text(resource, strings) ?: strings.notMeasuredYet.takeIf { failure == null },
            warning,
            failure?.reason(strings),
        )
        MachineStatusItem(
            resource = resource,
            name = resource.statusName(strings),
            figure = summary?.figure(resource, strings),
            tooltip = "${resource.title(strings)}: ${details.joinToString(" · ")}",
            attention = when {
                failure != null -> Status.DANGER
                warning != null -> Status.WARNING
                else -> null
            },
        )
    }

private fun Throwable.reason(strings: Strings): String {
    val described = strings.describe(this)
    val summary = if (this is WorktreeError.DockerUnavailable) strings.composeDockerUnavailable else described.summary.lines().first()
    return "${summary.trimEnd('.')}. ${described.nextStep}"
}

private fun MachineResource.statusName(strings: Strings): String = when (this) {
    MachineResource.GRADLE_DAEMONS -> strings.machineStatusGradle
    MachineResource.ANDROID_EMULATORS -> strings.machineStatusEmulators
    MachineResource.IOS_SIMULATORS -> strings.machineStatusSimulators
    MachineResource.XCODE_DERIVED_DATA -> strings.machineStatusDerivedData
    MachineResource.MAVEN_DAEMONS -> strings.machineStatusMaven
    MachineResource.COMPOSE_PROJECTS -> strings.machineStatusCompose
    MachineResource.DESKTOP_TURN -> strings.machineStatusDesktop
}

private fun MachineSummary.figure(resource: MachineResource, strings: Strings): String? = when (resource) {
    MachineResource.GRADLE_DAEMONS, MachineResource.ANDROID_EMULATORS, MachineResource.IOS_SIMULATORS ->
        if (poolInUse != null && poolLimit != null) strings.poolSummary(poolInUse, poolLimit) else count.toString()
    MachineResource.XCODE_DERIVED_DATA -> bytes?.takeUnless { calculating }?.let { used ->
        if (quotaBytes == null) formatSize(used) else "${formatSize(used)} / ${formatSize(quotaBytes)}"
    }
    MachineResource.MAVEN_DAEMONS, MachineResource.COMPOSE_PROJECTS -> count.toString()
    MachineResource.DESKTOP_TURN -> strings.desktopTurnSummary(holder, count)
}

private fun MachineSummary.warning(resource: MachineResource, strings: Strings): String? = when {
    poolKindOf(resource) != null && poolInUse != null && poolLimit != null && poolInUse > poolLimit -> strings.overLimit
    resource == MachineResource.XCODE_DERIVED_DATA && !calculating && bytes != null && quotaBytes != null && bytes > quotaBytes -> strings.overQuota
    else -> null
}
