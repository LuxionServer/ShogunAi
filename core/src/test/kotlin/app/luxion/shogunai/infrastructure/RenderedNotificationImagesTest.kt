package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.NoNotificationImages
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.NotificationItem
import app.luxion.shogunai.domain.model.NotifiedWorktree
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RenderedNotificationImagesTest {
    private val folder = Files.createTempDirectory("notification-images")
    private val images = folder.resolve("images")
    private val worktree = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")

    @AfterTest
    fun cleanUp() {
        folder.toFile().deleteRecursively()
    }

    private fun notification(vararg items: Pair<String, AgentState>) =
        AgentNotification(items.map { (name, state) -> NotificationItem(worktree, name, state) })

    @Test
    fun `one agent shows its initial in the colour of its state`() {
        assertEquals(NotificationBadge("C", needsYou = true), NotificationBadge.of(notification("Claude" to AgentState.BLOCKED)))
        assertEquals(NotificationBadge("C", needsYou = false), NotificationBadge.of(notification("codex" to AgentState.DONE)))
        assertEquals(NotificationBadge("?", needsYou = false), NotificationBadge.of(notification("" to AgentState.DONE)))
    }

    @Test
    fun `several agents show how many, up to nine, coloured by the first`() {
        assertEquals(NotificationBadge("2", needsYou = true), NotificationBadge.of(notification("Claude" to AgentState.BLOCKED, "Codex" to AgentState.DONE)))
        val many = notification(*Array(12) { "Claude" to AgentState.DONE })
        assertEquals("done-9.png", NotificationBadge.of(many).fileName)
        assertEquals("needs-you-c.png", NotificationBadge("C", needsYou = true).fileName)
    }

    @Test
    fun `the image is drawn once and reused`() {
        var renders = 0
        val rendered = RenderedNotificationImages(images) { badge -> renders++; renderBadge(badge) }

        val first = rendered.imageFor(notification("Claude" to AgentState.BLOCKED))
        val second = rendered.imageFor(notification("Claude" to AgentState.BLOCKED))

        assertEquals(images.resolve("needs-you-c.png").toString(), first)
        assertEquals(first, second)
        assertEquals(1, renders)
        assertEquals(256, ImageIO.read(images.resolve("needs-you-c.png").toFile()).width)
    }

    @Test
    fun `the two states are drawn in different colours`() {
        val blocked = renderBadge(NotificationBadge("C", needsYou = true))
        val done = renderBadge(NotificationBadge("C", needsYou = false))

        assertNotEquals(blocked.getRGB(20, 128), done.getRGB(20, 128))
    }

    @Test
    fun `an image that can't be written or drawn is left out`() {
        folder.resolve("blocked").writeText("a file, not a folder")
        val unwritable = RenderedNotificationImages(folder.resolve("blocked"))
        val noFonts = RenderedNotificationImages(images) { _: NotificationBadge -> throw UnsatisfiedLinkError("no fonts") }
        val noImage = RenderedNotificationImages(images) { _: NotificationBadge -> BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).also { throw IllegalStateException("x") } }

        assertNull(unwritable.imageFor(notification("Claude" to AgentState.DONE)))
        assertNull(noFonts.imageFor(notification("Claude" to AgentState.DONE)))
        assertNull(noImage.imageFor(notification("Codex" to AgentState.DONE)))
        assertNull(NoNotificationImages.imageFor(notification("Claude" to AgentState.DONE)))
    }
}
