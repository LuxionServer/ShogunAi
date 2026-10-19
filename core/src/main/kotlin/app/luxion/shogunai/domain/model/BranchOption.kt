package app.luxion.shogunai.domain.model

data class BranchOption(
    val name: String,
    val isCheckedOut: Boolean,
    val isRemote: Boolean = false,
)

const val REMOTE_PREFIX = "origin/"

fun List<BranchOption>.withoutLocalTwins(): List<BranchOption> {
    val localNames = filterNot { it.isRemote }.map { it.name }.toSet()
    return filterNot { it.isRemote && it.name.removePrefix(REMOTE_PREFIX) in localNames }
}
