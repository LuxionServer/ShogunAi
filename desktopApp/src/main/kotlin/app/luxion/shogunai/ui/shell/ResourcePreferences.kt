package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase

data class CustomLimitDraft(val text: String, val unlimited: Boolean, val invalid: Boolean = false)

class ResourcePreferences(
    private val repository: ResourceLimitsRepository,
    private val resolveLimits: ResolveResourceLimitsUseCase,
    val showsSimulators: Boolean,
) {
    var settings by mutableStateOf(repository.load())
        private set

    var drafts by mutableStateOf(draftsOf(settings.custom))
        private set

    val kinds: List<ResourceKind>
        get() = listOfNotNull(
            ResourceKind.EMULATOR,
            ResourceKind.IOS_SIMULATOR.takeIf { showsSimulators },
            ResourceKind.GRADLE_DAEMON,
        )

    val effectiveLimits: ResourceLimits get() = resolveLimits.limitsFor(settings)

    fun limitsOf(profile: CapacityProfile): ResourceLimits = resolveLimits.limitsFor(profile, settings.custom)

    fun choose(profile: CapacityProfile) {
        if (profile == settings.profile) return
        save(settings.copy(profile = profile))
    }

    fun draft(kind: ResourceKind): CustomLimitDraft = drafts.getValue(kind)

    fun edit(kind: ResourceKind, text: String) {
        val trimmed = text.trim()
        val number = trimmed.toIntOrNull()?.takeIf { it > 0 }
        if (number == null) {
            drafts = drafts + (kind to CustomLimitDraft(text, unlimited = false, invalid = true))
            return
        }
        drafts = drafts + (kind to CustomLimitDraft(number.toString(), unlimited = false))
        saveCustom(kind, number)
    }

    fun setUnlimited(kind: ResourceKind, unlimited: Boolean) {
        if (unlimited) {
            drafts = drafts + (kind to CustomLimitDraft("", unlimited = true))
            saveCustom(kind, null)
        } else {
            val restored = settings.custom.forKind(kind) ?: DEFAULT_CUSTOM_LIMIT
            drafts = drafts + (kind to CustomLimitDraft(restored.toString(), unlimited = false))
            saveCustom(kind, restored)
        }
    }

    private fun saveCustom(kind: ResourceKind, limit: Int?) {
        val current = settings.custom
        val updated = when (kind) {
            ResourceKind.EMULATOR -> current.copy(emulators = limit)
            ResourceKind.IOS_SIMULATOR -> current.copy(simulators = limit)
            ResourceKind.GRADLE_DAEMON -> current.copy(gradleDaemons = limit)
        }
        save(settings.copy(custom = updated))
    }

    private fun save(updated: ResourceSettings) {
        settings = updated
        repository.save(updated)
    }

    companion object {
        const val DEFAULT_CUSTOM_LIMIT = 2

        private fun draftsOf(custom: ResourceLimits): Map<ResourceKind, CustomLimitDraft> =
            ResourceKind.entries.associateWith { kind ->
                val limit = custom.forKind(kind)
                CustomLimitDraft(limit?.toString().orEmpty(), unlimited = limit == null)
            }

        val NONE = ResourcePreferences(
            repository = object : ResourceLimitsRepository {
                override fun load() = ResourceSettings()
                override fun save(settings: ResourceSettings) = Unit
            },
            resolveLimits = ResolveResourceLimitsUseCase(
                repository = object : ResourceLimitsRepository {
                    override fun load() = ResourceSettings()
                    override fun save(settings: ResourceSettings) = Unit
                },
                capacity = object : app.luxion.shogunai.domain.io.MachineCapacity {
                    override fun totalMemoryBytes(): Long? = null
                    override fun cores(): Int = 1
                },
            ),
            showsSimulators = false,
        )
    }
}
