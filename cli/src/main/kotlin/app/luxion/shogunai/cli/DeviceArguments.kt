package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DEFAULT_DEVICE_WAIT_MINUTES
import app.luxion.shogunai.domain.model.DevicePlatform
import java.time.Duration

enum class EmulatorAction { START, STOP, STATUS }

enum class SimulatorAction { START, STOP, STATUS }

internal fun parseSimulator(rest: List<String>): CliCommand {
    val name = rest.firstOrNull() ?: throw UsageException("'simulator' needs an action: start, stop, status or gradle-init")
    if (name == "gradle-init") return parseSimulatorGradleInit(rest.drop(1))
    val action = SimulatorAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'simulator', expected start, stop, status or gradle-init")
    var path: String? = null
    var device: String? = null
    var runtime: String? = null
    val wait = DeviceWaitOptions()
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--device" -> device = remaining.valueOf(argument)
            "--runtime" -> runtime = remaining.valueOf(argument)
            "--wait" -> wait.requested = true
            "--wait-timeout" -> wait.timeout(remaining.valueOf(argument))
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'simulator'")
                if (path != null) throw UsageException("'simulator $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != SimulatorAction.START && (device != null || runtime != null)) {
        throw UsageException("--device and --runtime only apply to 'simulator start'")
    }
    if (action != SimulatorAction.START && wait.requested) {
        throw UsageException("--wait and --wait-timeout only apply to 'simulator start'")
    }
    return CliCommand.Simulator(action, path, device, runtime, wait = wait.duration)
}

private fun parseSimulatorGradleInit(rest: List<String>): CliCommand.SimulatorGradleInit {
    val install = when (rest.firstOrNull()) {
        "install" -> true
        "uninstall" -> false
        null -> throw UsageException("'simulator gradle-init' needs install or uninstall")
        else -> throw UsageException("unknown action '${rest.first()}' for 'simulator gradle-init', expected install or uninstall")
    }
    var confirmed = false
    rest.drop(1).forEach { argument ->
        when (argument) {
            "--yes", "-y" -> confirmed = true
            else -> throw UsageException("unknown argument '$argument' for 'simulator gradle-init'")
        }
    }
    return CliCommand.SimulatorGradleInit(install, confirmed)
}

enum class HeadlessAction { START, STOP, STATUS, CAPTURE }

internal fun parseHeadless(rest: List<String>): CliCommand.Headless {
    val name = rest.firstOrNull() ?: throw UsageException("'headless' needs an action: start, stop, status or capture")
    val action = HeadlessAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'headless', expected start, stop, status or capture")
    var path: String? = null
    var visible = false
    var fresh = false
    var output: String? = null
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--visible" -> visible = true
            "--fresh" -> fresh = true
            "--output" -> output = remaining.valueOf(argument)
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'headless'")
                if (path != null) throw UsageException("'headless $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != HeadlessAction.START && (visible || fresh)) throw UsageException("--visible and --fresh only apply to 'headless start'")
    if (action != HeadlessAction.CAPTURE && output != null) throw UsageException("--output only applies to 'headless capture'")
    return CliCommand.Headless(action, path, visible, fresh, output)
}

enum class DesktopAction { ACQUIRE, RELEASE, STATUS }

internal fun parseDesktop(rest: List<String>): CliCommand.Desktop {
    val name = rest.firstOrNull() ?: throw UsageException("'desktop' needs an action: acquire, release or status")
    val action = DesktopAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'desktop', expected acquire, release or status")
    var path: String? = null
    val wait = DeviceWaitOptions()
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--wait" -> wait.requested = true
            "--wait-timeout" -> wait.timeout(remaining.valueOf(argument))
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'desktop'")
                if (path != null) throw UsageException("'desktop $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != DesktopAction.ACQUIRE && wait.requested) {
        throw UsageException("--wait and --wait-timeout only apply to 'desktop acquire'")
    }
    return CliCommand.Desktop(action, path, wait = wait.duration)
}

enum class DeviceAction { LIST, ACQUIRE, RELEASE, STATUS, LEND, WITHDRAW, RENAME, FORGET }

internal fun parseDevice(rest: List<String>): CliCommand.Device {
    val actions = "list, acquire, release, status, lend, withdraw, rename or forget"
    val name = rest.firstOrNull() ?: throw UsageException("'device' needs an action: $actions")
    val action = DeviceAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'device', expected $actions")
    var command = CliCommand.Device(action)
    val positional = mutableListOf<String>()
    val platforms = mutableSetOf<DevicePlatform>()
    val wait = DeviceWaitOptions()
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--all" -> command = command.copy(all = true)
            "--android" -> platforms += DevicePlatform.ANDROID
            "--ios" -> platforms += DevicePlatform.IOS
            "--name" -> command = command.copy(name = remaining.valueOf(argument))
            "--model" -> command = command.copy(model = remaining.valueOf(argument))
            "--wait" -> wait.requested = true
            "--wait-timeout" -> wait.timeout(remaining.valueOf(argument))
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'device'")
                positional += argument
            }
        }
    }
    if (platforms.size > 1) throw UsageException("'device $name' takes --android or --ios, not both")
    command = command.copy(platform = platforms.singleOrNull(), wait = wait.duration)
    requireDeviceOptions(command, name, wait.requested)
    return withDevicePositionals(command, name, positional)
}

private fun requireDeviceOptions(command: CliCommand.Device, name: String, waits: Boolean) {
    val acquire = command.action == DeviceAction.ACQUIRE
    if (command.all && command.action != DeviceAction.LIST) throw UsageException("--all only applies to 'device list'")
    if (!acquire && (command.name != null || command.model != null)) throw UsageException("--name and --model only apply to 'device acquire'")
    if (!acquire && waits) throw UsageException("--wait and --wait-timeout only apply to 'device acquire'")
    if (command.platform != null && !acquire && command.action != DeviceAction.RELEASE) {
        throw UsageException("--android and --ios only apply to 'device acquire' and 'device release', not 'device $name'")
    }
}

private fun withDevicePositionals(command: CliCommand.Device, name: String, positional: List<String>): CliCommand.Device = when (command.action) {
    DeviceAction.LIST -> command.also { if (positional.isNotEmpty()) throw UsageException("'device list' takes no arguments, got '${positional.first()}'") }
    DeviceAction.ACQUIRE, DeviceAction.RELEASE, DeviceAction.STATUS -> {
        if (positional.size > 1) throw UsageException("'device $name' takes at most one path")
        command.copy(path = positional.firstOrNull())
    }
    DeviceAction.RENAME -> {
        if (positional.size != 2) throw UsageException("'device rename' needs a device and its new alias")
        command.copy(device = positional[0], alias = positional[1])
    }
    DeviceAction.LEND, DeviceAction.WITHDRAW, DeviceAction.FORGET -> {
        if (positional.size != 1) throw UsageException("'device $name' needs one device: its id, alias or name")
        command.copy(device = positional.single())
    }
}

internal fun parseEmulator(rest: List<String>): CliCommand.Emulator {
    val name = rest.firstOrNull() ?: throw UsageException("'emulator' needs an action: start, stop or status")
    val action = EmulatorAction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw UsageException("unknown action '$name' for 'emulator', expected start, stop or status")
    var path: String? = null
    var avd: String? = null
    var noWindow = false
    val wait = DeviceWaitOptions()
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--avd" -> avd = remaining.valueOf(argument)
            "--no-window" -> noWindow = true
            "--wait" -> wait.requested = true
            "--wait-timeout" -> wait.timeout(remaining.valueOf(argument))
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'emulator'")
                if (path != null) throw UsageException("'emulator $name' takes at most one path")
                path = argument
            }
        }
    }
    if (action != EmulatorAction.START && (avd != null || noWindow)) {
        throw UsageException("--avd and --no-window only apply to 'emulator start'")
    }
    if (action != EmulatorAction.START && wait.requested) {
        throw UsageException("--wait and --wait-timeout only apply to 'emulator start'")
    }
    return CliCommand.Emulator(action, path, avd, noWindow, wait = wait.duration)
}

internal class DeviceWaitOptions {
    var requested = false
    private var minutes = DEFAULT_DEVICE_WAIT_MINUTES

    fun timeout(value: String) {
        minutes = value.toLongOrNull()?.takeIf { it > 0 }
            ?: throw UsageException("--wait-timeout needs a positive whole number of minutes, got '$value'")
        requested = true
    }

    val duration: Duration?
        get() = Duration.ofMinutes(minutes).takeIf { requested }
}
