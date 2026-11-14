package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class ResourcePreferencesTest {

    private val repository = FakeResourceLimitsRepository()

    private fun preferences(showsSimulators: Boolean = true) =
        ResourcePreferences(repository, ResolveResourceLimitsUseCase(repository, FakeMachineCapacity()), showsSimulators)

    @Test
    fun `the iOS simulators are a kind only where they are shown`() {
        assertEquals(listOf(ResourceKind.EMULATOR, ResourceKind.IOS_SIMULATOR, ResourceKind.GRADLE_DAEMON), preferences().kinds)
        assertEquals(listOf(ResourceKind.EMULATOR, ResourceKind.GRADLE_DAEMON), preferences(showsSimulators = false).kinds)
    }

    @Test
    fun `the effective limits follow the chosen profile`() {
        val resources = preferences()

        resources.choose(CapacityProfile.BALANCED)

        assertEquals(ResourceLimits.BALANCED, resources.effectiveLimits)
    }

    @Test
    fun `choosing the profile already chosen saves nothing`() {
        val resources = preferences()
        val saved = repository.settings

        resources.choose(resources.settings.profile)

        assertSame(saved, repository.settings)
    }

    @Test
    fun `saved custom limits without a figure show as unlimited`() {
        repository.settings = ResourceSettings(custom = ResourceLimits(emulators = 3))

        val resources = preferences()

        assertEquals(CustomLimitDraft("3", unlimited = false), resources.draft(ResourceKind.EMULATOR))
        assertEquals(CustomLimitDraft("", unlimited = true), resources.draft(ResourceKind.IOS_SIMULATOR))
    }

    @Test
    fun `limiting a kind again restores its saved figure or the default`() {
        repository.settings = ResourceSettings(custom = ResourceLimits(emulators = 3))
        val resources = preferences()

        resources.setUnlimited(ResourceKind.EMULATOR, false)
        resources.setUnlimited(ResourceKind.IOS_SIMULATOR, false)

        assertEquals(CustomLimitDraft("3", unlimited = false), resources.draft(ResourceKind.EMULATOR))
        assertEquals(CustomLimitDraft("2", unlimited = false), resources.draft(ResourceKind.IOS_SIMULATOR))
        assertEquals(ResourceLimits(emulators = 3, simulators = 2), repository.settings.custom)
    }

    @Test
    fun `each kind's figure is saved in its own limit`() {
        repository.settings = ResourceSettings(custom = ResourceLimits())
        val resources = preferences()

        resources.edit(ResourceKind.IOS_SIMULATOR, "4")
        resources.edit(ResourceKind.GRADLE_DAEMON, " 5 ")

        assertEquals(ResourceLimits(simulators = 4, gradleDaemons = 5), repository.settings.custom)
    }

    @Test
    fun `choosing the quota mode already chosen saves nothing`() {
        repository.settings = ResourceSettings(derivedDataQuotaMode = DerivedDataQuotaMode.FIXED)
        val resources = preferences()
        val saved = repository.settings

        resources.chooseQuotaMode(DerivedDataQuotaMode.FIXED)

        assertSame(saved, repository.settings)
    }

    @Test
    fun `the preferences that remember nothing use the defaults on a machine of unknown size`() {
        NoResourceLimits.save(ResourceSettings(profile = CapacityProfile.BALANCED))

        assertEquals(ResourceSettings(), NoResourceLimits.load())
        assertNull(NoMachineCapacity.totalMemoryBytes())
        assertEquals(1, NoMachineCapacity.cores())
        assertEquals(ResourceSettings(), ResourcePreferences.NONE.settings)
        assertFalse(ResourcePreferences.NONE.showsSimulators)
        assertEquals(ResourceLimits.LIGHT, ResourcePreferences.NONE.effectiveLimits)
    }
}
