package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitTicket

interface DeviceWaitQueue {

    fun read(kind: ResourceKind): List<WaitTicket>

    fun write(kind: ResourceKind, tickets: List<WaitTicket>)
}

interface ProcessLiveness {

    fun current(): ProcessIdentity

    fun isAlive(process: ProcessIdentity): Boolean
}
