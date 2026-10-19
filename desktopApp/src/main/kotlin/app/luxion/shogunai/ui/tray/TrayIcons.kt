package app.luxion.shogunai.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.theme.rememberSystemInDarkTheme
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

private const val MacMenuBarIconSize = 44
private const val ColoredIconSize = 64

@Composable
fun rememberTrayIconPainter(): Painter {
    val systemDark by rememberSystemInDarkTheme()
    val dark = isMacOs && systemDark
    return remember(dark) { BitmapPainter(trayIconImage(dark).toComposeImageBitmap()) }
}

fun trayIconImage(darkMenuBar: Boolean): BufferedImage =
    if (isMacOs) {
        drawHelmet(MacMenuBarIconSize, body = if (darkMenuBar) Color.WHITE else Color.BLACK, accent = null)
    } else {
        drawHelmet(ColoredIconSize, body = Color(0x3F, 0x4E, 0x5F), accent = Color(0xC0, 0x39, 0x2B))
    }

private fun drawHelmet(size: Int, body: Color, accent: Color?): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    graphics.scale(size.toDouble(), size.toDouble())
    graphics.color = body
    graphics.fill(dome())
    graphics.fill(horn(mirrored = false))
    graphics.fill(horn(mirrored = true))
    graphics.fill(neckGuard())
    graphics.fill(faceGuard())
    graphics.stroke = BasicStroke(0.04f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    if (accent != null) {
        graphics.color = accent
        graphics.draw(crest())
        graphics.fill(Ellipse2D.Double(0.46, 0.2, 0.08, 0.08))
    } else {
        graphics.composite = AlphaComposite.Clear
        graphics.draw(crest())
    }
    graphics.dispose()
    return image
}

private fun dome() = Path2D.Double().apply {
    moveTo(0.2, 0.62)
    curveTo(0.2, 0.36, 0.34, 0.26, 0.5, 0.26)
    curveTo(0.66, 0.26, 0.8, 0.36, 0.8, 0.62)
    closePath()
}

private fun horn(mirrored: Boolean) = Path2D.Double().apply {
    moveTo(0.24, 0.56)
    lineTo(0.06, 0.1)
    lineTo(0.42, 0.38)
    closePath()
    if (mirrored) transform(AffineTransform(-1.0, 0.0, 0.0, 1.0, 1.0, 0.0))
}

private fun neckGuard() = Path2D.Double().apply {
    moveTo(0.12, 0.62)
    lineTo(0.88, 0.62)
    lineTo(0.8, 0.8)
    lineTo(0.2, 0.8)
    closePath()
}

private fun faceGuard() = Path2D.Double().apply {
    moveTo(0.3, 0.8)
    lineTo(0.7, 0.8)
    lineTo(0.6, 0.97)
    lineTo(0.4, 0.97)
    closePath()
}

private fun crest() = Path2D.Double().apply {
    moveTo(0.5, 0.34)
    lineTo(0.5, 0.58)
}
