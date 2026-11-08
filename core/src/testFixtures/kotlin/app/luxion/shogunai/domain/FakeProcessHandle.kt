package app.luxion.shogunai.domain

import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class FakeProcessHandle(
    private val pid: Long = 42,
    private val alive: Boolean = true,
    private val command: String? = null,
    private val startedAt: Instant? = null,
) : ProcessHandle {
    override fun pid(): Long = pid
    override fun parent(): Optional<ProcessHandle> = Optional.empty()
    override fun children(): Stream<ProcessHandle> = Stream.empty()
    override fun descendants(): Stream<ProcessHandle> = Stream.empty()
    override fun onExit(): CompletableFuture<ProcessHandle> = CompletableFuture()
    override fun supportsNormalTermination(): Boolean = true
    override fun destroy(): Boolean = false
    override fun destroyForcibly(): Boolean = false
    override fun isAlive(): Boolean = alive
    override fun compareTo(other: ProcessHandle): Int = pid.compareTo(other.pid())
    override fun info(): ProcessHandle.Info = object : ProcessHandle.Info {
        override fun command(): Optional<String> = Optional.ofNullable(command)
        override fun commandLine(): Optional<String> = Optional.empty()
        override fun arguments(): Optional<Array<String>> = Optional.empty()
        override fun startInstant(): Optional<Instant> = Optional.ofNullable(startedAt)
        override fun totalCpuDuration(): Optional<Duration> = Optional.empty()
        override fun user(): Optional<String> = Optional.empty()
    }
}
