package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.queueName

interface DeviceWaitQueue {

    fun read(queue: WaitQueueName): List<WaitTicket>

    fun write(queue: WaitQueueName, tickets: List<WaitTicket>)

    fun read(kind: ResourceKind): List<WaitTicket> = read(kind.queueName)

    fun write(kind: ResourceKind, tickets: List<WaitTicket>) = write(kind.queueName, tickets)
}

interface ProcessLiveness {

    fun current(): ProcessIdentity

    fun isAlive(process: ProcessIdentity): Boolean
}
