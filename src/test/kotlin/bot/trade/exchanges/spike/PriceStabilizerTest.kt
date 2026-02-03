package bot.trade.exchanges.spike

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceStabilizerTest {

    private lateinit var stabilizer: PriceStabilizer
    private val config = SpikeConfig(
        enabled = true,
        stabilizationDelayMs = 15_000,
        stabilizationWindowMs = 10_000,
        stabilizationTolerancePercent = BigDecimal("0.3"),
        minPricePoints = 5,
        maxWaitTimeMs = 120_000
    )

    @BeforeEach
    fun setup() {
        stabilizer = PriceStabilizer(config)
    }

    @Test
    fun testNotStableBeforeDelay() {
        val spikeStart = 1000L
        val now = spikeStart + 10_000  // only 10s, delay is 15s

        repeat(10) { i ->
            stabilizer.recordPrice(BigDecimal("0.0340"), now - 500 + i * 50)
        }

        assertFalse(stabilizer.isStable(spikeStart, now))
    }

    @Test
    fun testNotStableWithTooFewPoints() {
        val spikeStart = 1000L
        val now = spikeStart + 20_000  // past delay

        // Only 3 points, need 5
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 2000)
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 1000)
        stabilizer.recordPrice(BigDecimal("0.0340"), now)

        assertFalse(stabilizer.isStable(spikeStart, now))
    }

    @Test
    fun testNotStableWithHighVolatility() {
        val spikeStart = 1000L
        val now = spikeStart + 20_000

        // Prices with > 0.3% spread: 0.034 to 0.0345 = ~1.47%
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 4000)
        stabilizer.recordPrice(BigDecimal("0.0341"), now - 3000)
        stabilizer.recordPrice(BigDecimal("0.0345"), now - 2000)
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 1000)
        stabilizer.recordPrice(BigDecimal("0.0344"), now)

        assertFalse(stabilizer.isStable(spikeStart, now))
    }

    @Test
    fun testStableWithLowVolatility() {
        val spikeStart = 1000L
        val now = spikeStart + 20_000

        // Prices within 0.3% of each other: 0.03400 to 0.03410 = ~0.29%
        stabilizer.recordPrice(BigDecimal("0.03400"), now - 4000)
        stabilizer.recordPrice(BigDecimal("0.03402"), now - 3000)
        stabilizer.recordPrice(BigDecimal("0.03405"), now - 2000)
        stabilizer.recordPrice(BigDecimal("0.03408"), now - 1000)
        stabilizer.recordPrice(BigDecimal("0.03410"), now)

        assertTrue(stabilizer.isStable(spikeStart, now))
    }

    @Test
    fun testTimeout() {
        val spikeStart = 1000L
        val now = spikeStart + config.maxWaitTimeMs + 1

        assertTrue(stabilizer.isTimedOut(spikeStart, now))
    }

    @Test
    fun testNotTimedOut() {
        val spikeStart = 1000L
        val now = spikeStart + config.maxWaitTimeMs - 1

        assertFalse(stabilizer.isTimedOut(spikeStart, now))
    }

    @Test
    fun testMedianPrice() {
        val now = 10_000L
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 4000)
        stabilizer.recordPrice(BigDecimal("0.0342"), now - 3000)
        stabilizer.recordPrice(BigDecimal("0.0345"), now - 2000)
        stabilizer.recordPrice(BigDecimal("0.0348"), now - 1000)
        stabilizer.recordPrice(BigDecimal("0.0350"), now)

        // Sorted: 0.0340, 0.0342, 0.0345, 0.0348, 0.0350 => median = 0.0345
        assertEquals(0, BigDecimal("0.0345").compareTo(stabilizer.getMedianPrice()))
    }

    @Test
    fun testMedianPriceEmpty() {
        assertEquals(0, BigDecimal.ZERO.compareTo(stabilizer.getMedianPrice()))
    }

    @Test
    fun testReset() {
        stabilizer.recordPrice(BigDecimal("0.0340"), 1000)
        stabilizer.recordPrice(BigDecimal("0.0342"), 2000)

        stabilizer.reset()

        assertEquals(0, BigDecimal.ZERO.compareTo(stabilizer.getMedianPrice()))
    }

    @Test
    fun testOldPricesEvicted() {
        val now = 50_000L

        // Old prices outside the window
        stabilizer.recordPrice(BigDecimal("0.0300"), now - config.stabilizationWindowMs - 5000)
        stabilizer.recordPrice(BigDecimal("0.0300"), now - config.stabilizationWindowMs - 4000)

        // Recent prices within window
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 4000)
        stabilizer.recordPrice(BigDecimal("0.0341"), now - 3000)
        stabilizer.recordPrice(BigDecimal("0.0340"), now - 2000)
        stabilizer.recordPrice(BigDecimal("0.0341"), now - 1000)
        stabilizer.recordPrice(BigDecimal("0.0340"), now)

        // Old prices should be evicted, so stability check only uses recent
        val spikeStart = now - 20_000
        assertTrue(stabilizer.isStable(spikeStart, now))
    }
}
