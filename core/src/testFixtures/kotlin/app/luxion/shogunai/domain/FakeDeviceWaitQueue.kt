package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket

class FakeDeviceWaitQueue : DeviceWaitQueue {

    val tickets = mutableMapOf<WaitQueueName, List<WaitTicket>>()

    override fun read(queue: WaitQueueName): List<WaitTicket> = tickets[queue].orEmpty()

    override fun write(queue: WaitQueueName, tickets: List<WaitTicket>) {
        if (tickets.isEmpty()) this.tickets.remove(queue) else this.tickets[queue] = tickets
    }
}

class FakeProcessLiveness(var currentProcess: ProcessIdentity = ProcessIdentity(1000, 1)) : ProcessLiveness {

    val dead = mutableSetOf<ProcessIdentity>()

    override fun current(): ProcessIdentity = currentProcess

    override fun isAlive(process: ProcessIdentity): Boolean = process !in dead
}
