package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.PendingSession

interface PendingNotificationStore {
    fun <T> update(block: (List<PendingSession>) -> Pair<List<PendingSession>, T>): T
}
