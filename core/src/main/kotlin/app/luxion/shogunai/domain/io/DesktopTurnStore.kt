package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DesktopTurn

interface DesktopTurnStore {

    fun read(): DesktopTurn?

    fun write(turn: DesktopTurn?)
}
