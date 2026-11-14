package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.domain.usecase.ProjectWorktreeSet
import app.luxion.shogunai.domain.usecase.attributeToProjects

data class MachineDerivedData(val folder: DerivedDataFolder, val owner: MachineDerivedDataOwner)

sealed interface MachineDerivedDataOwner {
    data class Project(val owner: ProjectOwner) : MachineDerivedDataOwner
    data object Orphaned : MachineDerivedDataOwner
    data object UnavailableVolume : MachineDerivedDataOwner
}

internal fun derivedOwnerOf(
    owner: DerivedDataOwner,
    folder: DerivedDataFolder,
    sets: List<ProjectWorktreeSet>,
): MachineDerivedDataOwner = when (owner) {
    DerivedDataOwner.Orphaned -> MachineDerivedDataOwner.Orphaned
    DerivedDataOwner.UnavailableVolume -> MachineDerivedDataOwner.UnavailableVolume
    is DerivedDataOwner.Worktree, DerivedDataOwner.OtherProject ->
        MachineDerivedDataOwner.Project(attributeToProjects(folder.workspacePath, sets) { true })
}
