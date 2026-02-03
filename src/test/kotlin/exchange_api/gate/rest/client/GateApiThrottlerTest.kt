package exchange_api.gate.rest.client

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GateApiThrottlerTest {

    @Test
    fun `awaitSlot enforces minimum delay`() {
        // Make test deterministic/fast
        System.setProperty("gateio.order.throttle.ms", "50")

        GateApiThrottler.awaitSlot("op1")
        val t2 = System.currentTimeMillis()
        GateApiThrottler.awaitSlot("op2")
        val t3 = System.currentTimeMillis()

        // There might be small scheduling inaccuracies; allow a small tolerance.
        assertTrue((t3 - t2) >= 40, "Expected throttling between sequential calls")

        // Clean up
        System.clearProperty("gateio.order.throttle.ms")
    }
}
