package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.model.DesktopTurn

class FakeDesktopTurnStore(var turn: DesktopTurn? = null) : DesktopTurnStore {

    override fun read(): DesktopTurn? = turn

    override fun write(turn: DesktopTurn?) {
        this.turn = turn
    }
}
