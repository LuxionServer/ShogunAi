package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.BYTES_PER_GIGABYTE
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.ResourceSettings

class ResolveDerivedDataQuotaUseCase(
    private val source: DerivedDataSource,
) {
    suspend operator fun invoke(settings: ResourceSettings, usageBytes: Long): DerivedDataQuota = when (settings.quotaMode) {
        DerivedDataQuotaMode.NONE -> DerivedDataQuota(DerivedDataQuotaMode.NONE, null)
        DerivedDataQuotaMode.FIXED -> DerivedDataQuota(DerivedDataQuotaMode.FIXED, settings.fixedDerivedDataQuotaBytes)
        DerivedDataQuotaMode.AUTOMATIC -> DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, runCatching { source.freeBytes() }.getOrNull()?.let { automatic(it, usageBytes) })
    }

    private fun automatic(freeBytes: Long, usageBytes: Long): Long =
        ((freeBytes + usageBytes) / AUTOMATIC_SHARE_DIVISOR).coerceIn(MIN_AUTOMATIC_QUOTA, MAX_AUTOMATIC_QUOTA)

    private companion object {
        const val AUTOMATIC_SHARE_DIVISOR = 4
        const val MIN_AUTOMATIC_QUOTA = 20 * BYTES_PER_GIGABYTE
        const val MAX_AUTOMATIC_QUOTA = 200 * BYTES_PER_GIGABYTE
    }
}
