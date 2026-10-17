package app.luxion.shogunai.ui.theme

import androidx.compose.ui.graphics.Color

enum class Status { SUCCESS, WARNING, INFO, DANGER, NEUTRAL }

data class StatusColor(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

data class ShogunStatusColors(
    val success: StatusColor,
    val warning: StatusColor,
    val info: StatusColor,
    val danger: StatusColor,
    val neutral: StatusColor,
) {
    fun of(status: Status): StatusColor = when (status) {
        Status.SUCCESS -> success
        Status.WARNING -> warning
        Status.INFO -> info
        Status.DANGER -> danger
        Status.NEUTRAL -> neutral
    }
}

val LightStatusColors = ShogunStatusColors(
    success = StatusColor(SuccessLight, Neutral100, SuccessContainerLight, OnSuccessContainerLight),
    warning = StatusColor(WarningLight, Neutral100, WarningContainerLight, OnWarningContainerLight),
    info = StatusColor(InfoLight, Neutral100, InfoContainerLight, OnInfoContainerLight),
    danger = StatusColor(DangerLight, Neutral100, DangerContainerLight, OnDangerContainerLight),
    neutral = StatusColor(Neutral38, Neutral100, Neutral89, Neutral18),
)

val DarkStatusColors = ShogunStatusColors(
    success = StatusColor(SuccessDark, OnSuccessDark, SuccessContainerDark, OnSuccessContainerDark),
    warning = StatusColor(WarningDark, OnWarningDark, WarningContainerDark, OnWarningContainerDark),
    info = StatusColor(InfoDark, OnInfoDark, InfoContainerDark, OnInfoContainerDark),
    danger = StatusColor(DangerDark, OnDangerDark, DangerContainerDark, OnDangerContainerDark),
    neutral = StatusColor(Neutral70, Neutral11, Neutral22, Neutral84),
)
