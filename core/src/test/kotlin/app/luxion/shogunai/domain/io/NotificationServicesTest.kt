package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.FakeNotificationPreferenceRepository
import app.luxion.shogunai.domain.FakeRunnerNotificationSource
import app.luxion.shogunai.domain.RecordingSystemNotifier
import app.luxion.shogunai.domain.model.AppRequest
import kotlin.test.Test
import kotlin.test.assertSame

class NotificationServicesTest {

    @Test
    fun `the services keep the ports they are given`() {
        val preferences = FakeNotificationPreferenceRepository()
        val runners = FakeRunnerNotificationSource()
        val notifier = RecordingSystemNotifier()
        val requests = object : AppRequestChannel {
            override fun send(request: AppRequest) = Unit
            override fun receive(nowMillis: Long): List<AppRequest> = emptyList()
        }

        val services = NotificationServices(preferences, runners, notifier, NoNotificationImages, requests)

        assertSame(preferences, services.preferences)
        assertSame(runners, services.runners)
        assertSame(notifier, services.notifier)
        assertSame(NoNotificationImages, services.images)
        assertSame(requests, services.requests)
    }
}
