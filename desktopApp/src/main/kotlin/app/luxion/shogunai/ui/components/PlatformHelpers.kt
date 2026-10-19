package app.luxion.shogunai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO
import java.awt.datatransfer.StringSelection

private object AppIconResource

@Composable
fun rememberAppIconPainter(): Painter = remember {
    BitmapPainter(ImageIO.read(checkNotNull(AppIconResource::class.java.getResourceAsStream("/icons/icon.png"))).toComposeImageBitmap())
}

@OptIn(ExperimentalComposeUiApi::class)
suspend fun Clipboard.copyText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
}
