package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.model.AppRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TrayNotifierTest {
    private val notification = SystemNotification("Claude needs you", "feature/login · api", "Open", AppRequest.openApp(0))

    @Test
    fun `the balloon shows the title and body and reports its click`() = runTest {
        val shown = mutableListOf<Pair<String, String>>()
        var clicked = 0
        val balloon = object : TrayBalloon {
            override fun display(title: String, body: String, onClick: () -> Unit) {
                shown += title to body
                onClick()
            }
        }

        TrayNotifier(balloon).show(notification) { clicked++ }

        assertEquals(listOf("Claude needs you" to "feature/login · api"), shown)
        assertEquals(1, clicked)
    }

    @Test
    fun `a tray that can't show balloons is quiet`() = runTest {
        val balloon = object : TrayBalloon {
            override fun display(title: String, body: String, onClick: () -> Unit) = throw UnsupportedOperationException("no tray")
        }

        TrayNotifier(balloon).show(notification) {}
    }
}
