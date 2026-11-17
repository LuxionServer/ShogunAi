package app.luxion.shogunai

import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase

data class XcodeUseCases(
    val provider: suspend () -> XcodeProvider?,
    val supported: suspend () -> Boolean,
    val list: ListDerivedDataUseCase,
    val deleteOrphans: DeleteOrphanedDerivedDataUseCase,
)
