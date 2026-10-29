package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.RunnerId

fun Strings.openIn(runner: RunnerId): String = when (runner) {
    RunnerId.HERDR -> openInHerdr
    RunnerId.ORCA -> openInOrca
}
