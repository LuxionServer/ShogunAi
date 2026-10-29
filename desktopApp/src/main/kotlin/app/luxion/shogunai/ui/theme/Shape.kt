package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ControlShape = RoundedCornerShape(4.dp)
val RowShape = RoundedCornerShape(6.dp)
val PanelShape = RoundedCornerShape(8.dp)
val DialogShape = RoundedCornerShape(12.dp)

val ShogunShapes = Shapes(
    extraSmall = ControlShape,
    small = RowShape,
    medium = PanelShape,
    large = DialogShape,
    extraLarge = DialogShape,
)
