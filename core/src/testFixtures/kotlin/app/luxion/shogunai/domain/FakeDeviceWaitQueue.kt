package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitTicket

class FakeDeviceWaitQueue : DeviceWaitQueue {

    val tickets = mutableMapOf<ResourceKind, List<WaitTicket>>()

    override fun read(kind: ResourceKind): List<WaitTicket> = tickets[kind].orEmpty()

    override fun write(kind: ResourceKind, tickets: List<WaitTicket>) {
        if (tickets.isEmpty()) this.tickets.remove(kind) else this.tickets[kind] = tickets
    }
}

class FakeProcessLiveness(var currentProcess: ProcessIdentity = ProcessIdentity(1000, 1)) : ProcessLiveness {

    val dead = mutableSetOf<ProcessIdentity>()

    override fun current(): ProcessIdentity = currentProcess

    override fun isAlive(process: ProcessIdentity): Boolean = process !in dead
}
