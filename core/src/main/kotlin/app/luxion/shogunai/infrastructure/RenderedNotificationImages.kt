package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.model.AgentNotification
import app.luxion.shogunai.domain.model.AgentState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

data class NotificationBadge(val label: String, val needsYou: Boolean) {
    val fileName: String get() = "${if (needsYou) "needs-you" else "done"}-${label.lowercase()}.png"

    companion object {
        fun of(notification: AgentNotification): NotificationBadge {
            val first = notification.items.first()
            val label = if (notification.items.size > 1) notification.items.size.coerceAtMost(MAX_COUNT).toString() else first.agentName.take(1).uppercase()
            return NotificationBadge(label.ifEmpty { "?" }, first.state == AgentState.BLOCKED)
        }

        private const val MAX_COUNT = 9
    }
}

class RenderedNotificationImages(
    private val directory: Path,
    private val render: (NotificationBadge) -> BufferedImage = ::renderBadge,
) : NotificationImages {

    override fun imageFor(notification: AgentNotification): String? = try {
        val badge = NotificationBadge.of(notification)
        val file = directory.resolve(badge.fileName)
        if (!Files.isRegularFile(file)) write(badge, file)
        file.toString()
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }

    private fun write(badge: NotificationBadge, file: Path) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, badge.fileName, ".tmp")
        ImageIO.write(render(badge), "png", temporary.toFile())
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

fun renderBadge(badge: NotificationBadge): BufferedImage {
    val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    val color = if (badge.needsYou) NEEDS_YOU else DONE
    graphics.color = color
    graphics.fill(RoundRectangle2D.Double(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble(), CORNER, CORNER))
    drawLabel(graphics, badge.label)
    drawMark(graphics, badge.needsYou, color)
    graphics.dispose()
    return image
}

private fun drawLabel(graphics: Graphics2D, label: String) {
    graphics.color = Color.WHITE
    graphics.font = Font(Font.SANS_SERIF, Font.BOLD, LABEL_SIZE)
    val metrics = graphics.fontMetrics
    val x = (SIZE - metrics.stringWidth(label)) / 2
    val y = (SIZE - metrics.height) / 2 + metrics.ascent
    graphics.drawString(label, x, y)
}

private fun drawMark(graphics: Graphics2D, needsYou: Boolean, color: Color) {
    graphics.color = Color.WHITE
    graphics.fillOval(MARK_ORIGIN, MARK_ORIGIN, MARK_SIZE, MARK_SIZE)
    graphics.color = color
    graphics.stroke = BasicStroke(MARK_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val centre = MARK_ORIGIN + MARK_SIZE / 2.0
    val mark = Path2D.Double()
    if (needsYou) {
        mark.moveTo(centre, centre - 18)
        mark.lineTo(centre, centre + 4)
        mark.moveTo(centre, centre + 16)
        mark.lineTo(centre, centre + 17)
    } else {
        mark.moveTo(centre - 15, centre)
        mark.lineTo(centre - 4, centre + 11)
        mark.lineTo(centre + 15, centre - 11)
    }
    graphics.draw(mark)
}

private const val SIZE = 256
private const val CORNER = 64.0
private const val LABEL_SIZE = 150
private const val MARK_SIZE = 76
private const val MARK_ORIGIN = SIZE - MARK_SIZE - 10
private const val MARK_STROKE = 10f
private val NEEDS_YOU = Color(0xD9, 0x77, 0x06)
private val DONE = Color(0x1F, 0x8A, 0x5B)
