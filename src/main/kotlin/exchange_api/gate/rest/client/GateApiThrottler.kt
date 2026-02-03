package exchange_api.gate.rest.client

import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong

/**
 * Very small client-side throttler to avoid hitting Gate.io REST rate limits when placing/canceling orders.
 *
 * Gate.io limits are per API key; in our app multiple bots/threads may place orders concurrently.
 * This throttler is global (JVM-wide) and enforces a minimum delay between requests.
 */
internal object GateApiThrottler {
    private val log = KotlinLogging.logger {}

    private val lastRequestAtMs = AtomicLong(0)
    private val lock = Any()

    /**
     * Minimum delay between Gate.io trade requests.
     *
     * Configured via:
     * - system property: gateio.order.throttle.ms
     * - env var: GATEIO_ORDER_THROTTLE_MS
     */
    fun minDelayMs(): Long {
        val fromProp = System.getProperty("gateio.order.throttle.ms")?.toLongOrNull()
        if (fromProp != null && fromProp >= 0) return fromProp

        val fromEnv = System.getenv("GATEIO_ORDER_THROTTLE_MS")?.toLongOrNull()
        if (fromEnv != null && fromEnv >= 0) return fromEnv

        // Default: a few seconds, to be conservative.
        return 2500L
    }

    fun awaitSlot(operation: String) {
        val delay = minDelayMs()
        if (delay <= 0) return

        synchronized(lock) {
            val now = System.currentTimeMillis()
            val last = lastRequestAtMs.get()
            val nextAllowed = last + delay
            val sleepMs = nextAllowed - now

            if (sleepMs > 0) {
                log.debug { "Gate.io throttle: sleeping ${sleepMs}ms before $operation" }
                try {
                    Thread.sleep(sleepMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestAtMs.set(System.currentTimeMillis())
        }
    }
}
