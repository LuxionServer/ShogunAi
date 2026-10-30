package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlinx.coroutines.test.runTest
import app.luxion.shogunai.domain.io.DerivedDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveDerivedDataQuotaUseCaseTest {

    private val gb = BYTES_PER_GIGABYTE
    private val automatic = ResourceSettings()

    private suspend fun resolve(settings: ResourceSettings, usageGb: Long, freeGb: Long?): DerivedDataQuota =
        ResolveDerivedDataQuotaUseCase(FakeDerivedDataSource(free = freeGb?.let { it * gb }))(settings, usageGb * gb)

    @Test
    fun `automatic is a quarter of the free space plus the usage`() = runTest {
        assertEquals(DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, 40 * gb), resolve(automatic, usageGb = 60, freeGb = 100))
    }

    @Test
    fun `automatic is at least 20 GB on a nearly full disk`() = runTest {
        assertEquals(20 * gb, resolve(automatic, usageGb = 30, freeGb = 10).bytes)
    }

    @Test
    fun `automatic is at most 200 GB on a large free disk`() = runTest {
        assertEquals(200 * gb, resolve(automatic, usageGb = 0, freeGb = 2048).bytes)
    }

    @Test
    fun `automatic is unknown when the free space can't be read`() = runTest {
        val quota = resolve(automatic, usageGb = 30, freeGb = null)

        assertEquals(DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, null), quota)
        assertTrue(quota.isUnknown)
    }

    @Test
    fun `a fixed quota ignores the disk`() = runTest {
        val fixed = ResourceSettings(derivedDataQuotaGigabytes = 50, derivedDataQuotaMode = DerivedDataQuotaMode.FIXED)

        assertEquals(DerivedDataQuota(DerivedDataQuotaMode.FIXED, 50 * gb), resolve(fixed, usageGb = 60, freeGb = 100))
    }

    @Test
    fun `no quota has no size and isn't unknown`() = runTest {
        val none = ResourceSettings(derivedDataQuotaGigabytes = 50, derivedDataQuotaMode = DerivedDataQuotaMode.NONE)
        val quota = resolve(none, usageGb = 60, freeGb = 100)

        assertEquals(DerivedDataQuota(DerivedDataQuotaMode.NONE, null), quota)
        assertFalse(quota.isUnknown)
    }

    @Test
    fun `automatic is unknown when reading the free space fails`() = runTest {
        val source = object : DerivedDataSource by FakeDerivedDataSource() {
            override suspend fun freeBytes(): Long? = throw IllegalStateException("statfs failed")
        }

        assertEquals(DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, null), ResolveDerivedDataQuotaUseCase(source)(automatic, 30 * gb))
    }
}
