package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.Composable

@Composable
fun MachineView(resource: MachineResource, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    when (resource) {
        MachineResource.GRADLE_DAEMONS -> GradleDaemonsView(machine, onOpenWorktree)
        MachineResource.ANDROID_EMULATORS -> AndroidEmulatorsView(machine, onOpenWorktree)
        MachineResource.IOS_SIMULATORS -> IosSimulatorsView(machine, onOpenWorktree)
        MachineResource.XCODE_DERIVED_DATA -> XcodeDerivedDataView(machine, onOpenWorktree)
    }
}
