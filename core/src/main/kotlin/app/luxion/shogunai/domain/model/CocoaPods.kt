package app.luxion.shogunai.domain.model

enum class PodsInstallReason { BASE_HAS_NO_PODS, BASE_PODS_DIFFER, CLONE_FAILED }

data class PodsInstallNeeded(val folder: String, val reason: PodsInstallReason)

data class PodsPreparation(
    val cloned: List<String> = emptyList(),
    val installNeeded: List<PodsInstallNeeded> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean get() = cloned.isEmpty() && installNeeded.isEmpty() && error == null
}
