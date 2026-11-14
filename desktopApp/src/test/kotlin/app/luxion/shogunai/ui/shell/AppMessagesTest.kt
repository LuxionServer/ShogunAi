package app.luxion.shogunai.ui.shell

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AppMessagesTest {

    @Test
    fun `a message without an action goes away after the short time`() = runTest {
        val messages = AppMessages(backgroundScope)

        messages.show("Copied")
        runCurrent()
        assertEquals("Copied", messages.hostState.currentSnackbarData?.visuals?.message)
        assertNull(messages.hostState.currentSnackbarData?.visuals?.actionLabel)

        advanceTimeBy(AppMessages.SHORT_DURATION + 1.seconds)
        assertNull(messages.hostState.currentSnackbarData)
    }

    @Test
    fun `a message's action runs when it is chosen`() = runTest {
        var undone = 0
        val messages = AppMessages(backgroundScope)

        messages.show("Removed", MessageAction("Undo") { undone++ })
        runCurrent()
        assertEquals("Undo", messages.hostState.currentSnackbarData?.visuals?.actionLabel)
        messages.hostState.currentSnackbarData?.performAction()
        runCurrent()

        assertEquals(1, undone)
    }

    @Test
    fun `a message with an action that is dismissed or times out doesn't run it`() = runTest {
        var undone = 0
        val messages = AppMessages(backgroundScope)

        messages.show("Removed", MessageAction("Undo") { undone++ })
        runCurrent()
        messages.hostState.currentSnackbarData?.dismiss()
        runCurrent()
        messages.show("Removed again", MessageAction("Undo") { undone++ })
        runCurrent()
        advanceTimeBy(AppMessages.WITH_ACTION_DURATION + 1.seconds)

        assertNull(messages.hostState.currentSnackbarData)
        assertEquals(0, undone)
    }

    @Test
    fun `a new message replaces the one shown`() = runTest {
        val messages = AppMessages(backgroundScope)

        messages.show("First")
        runCurrent()
        messages.show("Second")
        runCurrent()

        assertEquals("Second", messages.hostState.currentSnackbarData?.visuals?.message)
    }
}
