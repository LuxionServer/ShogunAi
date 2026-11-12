package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceAcquisition
import app.luxion.shogunai.domain.model.DeviceBlock
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceRelease
import app.luxion.shogunai.domain.model.HeldDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceWaitProgress
import app.luxion.shogunai.domain.model.PhysicalDevicesState
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.SHOGUN_ANDROID_DEVICE_SERIAL
import app.luxion.shogunai.domain.model.SHOGUN_IOS_DEVICE_UDID
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.repositoryPath
import app.luxion.shogunai.domain.model.repositoryPathOf
import app.luxion.shogunai.domain.model.toLastSeenText
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal fun deviceListLines(state: PhysicalDevicesState, all: Boolean): List<String> {
    val shown = state.devices.filter { all || it.connected != null }
    val pending = state.pending.map { "${it.transport}  ${it.platform.label}  ${it.usability.note}" }
    return (shown.map(::deviceListLine) + pending).ifEmpty { listOf(if (all) "no physical device recorded" else "no physical device connected") }
}

private fun deviceListLine(overview: DeviceOverview): String {
    val device = overview.device
    val details = buildList {
        add(device.platform.label)
        device.model?.let(::add)
        device.osVersion?.let { add("${device.platform.label} $it") }
        val connected = overview.connected
        if (connected == null) {
            add("disconnected, last seen ${Instant.ofEpochMilli(device.lastSeen).toLastSeenText()}")
        } else {
            add(connected.connection.label)
            if (!connected.isUsable) add(connected.usability.note)
        }
        add(if (device.lent) "lent" else "withdrawn")
        overview.holder?.let { add("held by ${it.holderName} until ${it.expiresAt.toLocalClockTime()}") }
    }
    return "${device.displayName} (${device.id})  " + details.joinToString("  ")
}

internal fun deviceListJson(state: PhysicalDevicesState, all: Boolean) = buildJsonArray {
    state.devices.filter { all || it.connected != null }.forEach { add(deviceJson(it)) }
}

internal fun deviceJson(overview: DeviceOverview): JsonObject = buildJsonObject {
    putRecorded(overview.device)
    put("connection", overview.connected?.let { it.connection.key })
    put("usability", overview.connected?.let { it.usability.key })
    putReference(overview.device.platform, overview.connected?.transport, overview.device.id)
    put("lent", overview.device.lent)
    put("lastSeen", isoTime(overview.device.lastSeen))
    put("holder", overview.holder?.let(::holderJson).orJsonNull())
}

private fun JsonObjectBuilder.putRecorded(device: RecordedDevice) {
    put("id", device.id)
    put("alias", device.alias)
    put("name", device.name)
    put("platform", device.platform.key)
    put("manufacturer", device.manufacturer)
    put("model", device.model)
    put("marketingName", device.marketingName)
    put("osVersion", device.osVersion)
}

private fun JsonObjectBuilder.putReference(platform: DevicePlatform, transport: String?, id: String) {
    when (platform) {
        DevicePlatform.ANDROID -> put("serial", transport)
        DevicePlatform.IOS -> put("udid", id.substringAfter(':'))
    }
}

private fun holderJson(held: HeldDevice): JsonObject = buildJsonObject {
    put("repository", repositoryPathOf(held.lease.adminDirectory))
    put("worktree", held.lease.worktreePath)
    put("since", isoTime(held.lease.acquiredAt))
    put("expiresAt", held.expiresAt.toString())
    put("agentWorking", held.agentState == AgentState.WORKING)
}

internal fun heldDeviceJson(held: HeldDevice): JsonObject = buildJsonObject {
    put("id", held.device.id)
    put("alias", held.device.alias)
    put("name", held.device.name)
    put("model", held.device.model)
    put("marketingName", held.device.marketingName)
    put("connection", held.connected?.let { it.connection.key })
    put("connected", held.isConnected)
    putReference(held.lease.platform, held.connected?.transport, held.device.id)
    put("expiresAt", held.expiresAt.toString())
}

internal fun deviceVariable(held: HeldDevice): String = when (held.lease.platform) {
    DevicePlatform.ANDROID -> "export $SHOGUN_ANDROID_DEVICE_SERIAL=${held.connected?.transport ?: held.device.id.substringAfter(':')}"
    DevicePlatform.IOS -> "export $SHOGUN_IOS_DEVICE_UDID=${held.device.id.substringAfter(':')}"
}

internal fun deviceAcquisitionLines(acquisition: DeviceAcquisition): List<String> {
    val held = acquisition.held
    val verb = if (acquisition.renewed) "already held" else "acquired"
    val taken = acquisition.takenFrom?.let { ", taken from $it" }.orEmpty()
    return listOf("$verb ${held.lease.platform.label} device ${heldSummary(held)}$taken", deviceVariable(held))
}

private fun heldSummary(held: HeldDevice): String {
    val device = held.device
    val model = device.marketingName ?: device.model
    val name = if (model != null && model != device.displayName) "${device.displayName} ($model)" else device.displayName
    val connected = held.connected
    val connection = if (connected == null) "disconnected" else "${reference(held)}, ${connected.connection.label}"
    return "$name, $connection, lease until ${held.expiresAt.toLocalClockTime()}"
}

private fun reference(held: HeldDevice): String = when (held.lease.platform) {
    DevicePlatform.ANDROID -> "serial ${held.connected?.transport}"
    DevicePlatform.IOS -> "UDID ${held.device.id.substringAfter(':')}"
}

internal fun deviceAcquisitionJson(acquisition: DeviceAcquisition, orcaView: String?): JsonObject = buildJsonObject {
    DevicePlatform.entries.forEach { platform ->
        put(platform.key, acquisition.held.takeIf { it.lease.platform == platform }?.let(::heldDeviceJson).orJsonNull())
    }
    put("renewed", acquisition.renewed)
    put("waited", acquisition.waitedSeconds)
    put("takenFrom", acquisition.takenFrom)
    put("orcaView", orcaView)
}

internal fun deviceReleaseLines(release: DeviceRelease): List<String> =
    release.released.map { "released ${it.lease.platform.label} device ${it.device.displayName}" }.ifEmpty { listOf("held no physical device") }

internal fun deviceReleaseJson(release: DeviceRelease): JsonObject = buildJsonObject {
    DevicePlatform.entries.forEach { platform ->
        put(platform.key, release.released.firstOrNull { it.lease.platform == platform }?.let(::heldDeviceJson).orJsonNull())
    }
}

internal fun deviceStatusLines(state: PhysicalDevicesState, admin: String): List<String> = DevicePlatform.entries.map { platform ->
    val held = state.heldBy(admin, platform)
    val waiting = state.waitingOf(admin, platform)
    val prefix = "${platform.key}: "
    when {
        held != null -> prefix + heldSummary(held)
        waiting != null -> prefix + "waiting, ${ordinal(waiting.first)} of ${state.waiting.getValue(platform).size} in line since ${clockTime(waiting.second.since)}"
        else -> prefix + "none" + state.lossOf(admin, platform)?.let { " (${lossText(it)})" }.orEmpty()
    }
}

internal fun lossText(loss: DeviceLoss): String = when (loss.reason) {
    DeviceLossReason.TAKEN -> "${loss.deviceName} was taken for ${loss.forWorktree} at ${clockTime(loss.at)}"
    DeviceLossReason.WITHDRAWN -> "${loss.deviceName} was withdrawn by the user at ${clockTime(loss.at)}"
}

internal fun deviceStatusJson(state: PhysicalDevicesState, admin: String): JsonObject = buildJsonObject {
    DevicePlatform.entries.forEach { platform ->
        val waiting = state.waitingOf(admin, platform)
        put(
            platform.key,
            buildJsonObject {
                put("device", state.heldBy(admin, platform)?.let(::heldDeviceJson).orJsonNull())
                put(
                    "waiting",
                    waiting?.let { (position, ticket) ->
                        buildJsonObject {
                            put("position", position)
                            put("since", isoTime(ticket.since))
                        }
                    }.orJsonNull(),
                )
                put("lost", state.lossOf(admin, platform)?.let(::lossJson).orJsonNull())
            },
        )
    }
}

private fun lossJson(loss: DeviceLoss): JsonObject = buildJsonObject {
    put("id", loss.deviceId)
    put("name", loss.deviceName)
    put("reason", loss.reason.key)
    put("forWorktree", loss.forWorktree)
    put("at", isoTime(loss.at))
}

internal fun deviceStatusNotes(state: PhysicalDevicesState?, worktreePath: String): List<String> {
    if (state == null) return emptyList()
    return DevicePlatform.entries.mapNotNull { platform ->
        val held = state.devices.mapNotNull { it.holder }.firstOrNull { it.lease.platform == platform && samePath(it.lease.worktreePath, worktreePath) }
        val queue = state.waiting.getValue(platform)
        val position = queue.indexOfFirst { samePath(it.worktreePath, worktreePath) }
        when {
            held != null -> "${platform.key}-device(${held.device.displayName}${if (held.isConnected) "" else ", disconnected"})"
            position >= 0 -> "${platform.key}-device(waiting ${position + 1}/${queue.size})"
            else -> null
        }
    }
}

internal fun worktreeDevicesJson(state: PhysicalDevicesState?, worktreePath: String): JsonObject = buildJsonObject {
    DevicePlatform.entries.forEach { platform ->
        put(platform.key, state?.let { worktreeDeviceJson(it, platform, worktreePath) }.orJsonNull())
    }
}

private fun worktreeDeviceJson(state: PhysicalDevicesState, platform: DevicePlatform, worktreePath: String): JsonObject? {
    val held = state.devices.mapNotNull { it.holder }.firstOrNull { it.lease.platform == platform && samePath(it.lease.worktreePath, worktreePath) }
    val queue = state.waiting.getValue(platform)
    val position = queue.indexOfFirst { samePath(it.worktreePath, worktreePath) }
    return when {
        held != null -> buildJsonObject {
            put("role", "holder")
            put("id", held.device.id)
            put("alias", held.device.alias)
            put("model", held.device.marketingName ?: held.device.model)
            put("connected", held.isConnected)
            put("expiresAt", held.expiresAt.toString())
        }
        position >= 0 -> buildJsonObject {
            put("role", "waiting")
            put("position", position + 1)
            put("since", isoTime(queue[position].since))
        }
        else -> null
    }
}

internal fun devicePoolLines(state: PhysicalDevicesState): List<String> = DevicePlatform.entries.flatMap { platform ->
    val lent = state.devices.filter { it.device.platform == platform && it.device.lent }
    val waiting = state.waiting.getValue(platform)
    if (lent.isEmpty() && waiting.isEmpty()) return@flatMap emptyList()
    val connected = lent.filter { it.connected != null }
    val free = lent.count { it.holder == null && it.connected?.isUsable == true }
    val shown = lent.filter { it.connected != null || it.holder != null }
    listOf("${platform.label} physical devices: $free of ${connected.size} free") +
        shown.map { overview ->
            val holder = overview.holder
            val name = overview.device.displayName
            if (holder == null) "  $name free" else "  $name held by ${holder.holderName} until ${holder.expiresAt.toLocalClockTime()}" + if (holder.isConnected) "" else ", disconnected"
        } +
        waiting.mapIndexed { index, ticket -> "  ${index + 1}. ${ticket.displayName} waiting since ${clockTime(ticket.since)}" }
}

internal fun devicePoolJson(state: PhysicalDevicesState): JsonObject = buildJsonObject {
    DevicePlatform.entries.forEach { platform ->
        put(
            platform.key,
            buildJsonObject {
                put("devices", buildJsonArray { state.devices.filter { it.device.platform == platform && it.device.lent }.forEach { add(deviceJson(it)) } })
                put("waiting", buildJsonArray { state.waiting.getValue(platform).forEach { add(deviceWaiterJson(it)) } })
            },
        )
    }
}

private fun deviceWaiterJson(ticket: WaitTicket): JsonObject = buildJsonObject {
    put("repository", ticket.repositoryPath)
    put("worktree", ticket.worktreePath)
    put("since", isoTime(ticket.since))
}

internal fun deviceWaitProgressLine(progress: PhysicalDeviceWaitProgress): String {
    val reasons = progress.blocks.map { block ->
        when (block) {
            is DeviceBlock.Held -> "${block.device.device.displayName} held by ${block.device.holderName}" +
                if (block.device.agentState == AgentState.WORKING) " (agent working)" else " until ${block.device.expiresAt.toLocalClockTime()}"
            is DeviceBlock.Disconnected -> "${block.device.displayName} isn't connected; connect it"
            is DeviceBlock.Withdrawn -> "${block.device.displayName} isn't lent to agents"
            is DeviceBlock.Unusable -> "${block.device.displayName}: ${block.usability.note}"
        }
    }
    val line = "waiting for an ${progress.platform.label} device: ${ordinal(progress.position)} of ${progress.waiting} in line"
    return if (reasons.isEmpty()) "$line, behind earlier waiters" else "$line; " + reasons.joinToString("; ")
}

internal fun recordedDeviceLine(device: RecordedDevice, change: String): String = "${device.displayName} (${device.id}) $change"

internal fun recordedDeviceJson(device: RecordedDevice): JsonObject = buildJsonObject {
    putRecorded(device)
    put("lent", device.lent)
    put("lastSeen", isoTime(device.lastSeen))
}

private fun samePath(first: String, second: String): Boolean = first.replace('\\', '/').trimEnd('/') == second.replace('\\', '/').trimEnd('/')

private fun clockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toLocalClockTime()

private fun isoTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
