package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.usecase.BranchDeletion

internal fun BranchDeletion.keptReason(): String? = when (this) {
    BranchDeletion.Deleted -> null
    BranchDeletion.KeptNotMerged -> "not merged into its base or the main branch"
    is BranchDeletion.KeptFailed -> reason.ifBlank { "Git couldn't delete it" }
}

internal fun BranchDeletion.keptBecause(): String? = when (this) {
    BranchDeletion.Deleted -> null
    BranchDeletion.KeptNotMerged -> "it isn't merged into its base or the main branch"
    is BranchDeletion.KeptFailed -> "Git couldn't delete it: ${reason.ifBlank { "no reason given" }}"
}
