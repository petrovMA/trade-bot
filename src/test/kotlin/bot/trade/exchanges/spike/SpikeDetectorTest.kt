package bot.trade.exchanges.spike

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SpikeDetectorTest {

    private lateinit var detector: SpikeDetector
    private val config = SpikeConfig(
        enabled = true,
        threshold = 3,
        timeWindowMs = 10_000
    )

    @BeforeEach
    fun setup() {
        detector = SpikeDetector(config)
    }

    private fun makeOrder(id: Long, side: SIDE, amount: BigDecimal = BigDecimal("100")): ActiveOrder =
        ActiveOrder(
            id = id,
            botName = "test-bot",
            orderId = "order-$id",
            tradePair = "REACT_USDT",
            amount = amount,
            orderSide = side,
            price = BigDecimal("0.035"),
            stopPrice = BigDecimal("0.036"),
            direction = DIRECTION.LONG
        )

    @Test
    fun testNoSpikeWithFewOrders() {
        val now = 1000L
        assertFalse(detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now))
        assertFalse(detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000))
        assertFalse(detector.isActive())
    }

    @Test
    fun testSpikeDetectedAtThreshold() {
        val now = 1000L
        assertFalse(detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now))
        assertFalse(detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000))
        assertTrue(detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), now + 2000))
        assertTrue(detector.isActive())
        assertEquals(now, detector.getSpikeStartTime())
    }

    @Test
    fun testOldFillsExpire() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000)

        // Third fill comes after the time window
        val laterTime = now + config.timeWindowMs + 1
        assertFalse(detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), laterTime))
        assertFalse(detector.isActive())
    }

    @Test
    fun testMixedSidesDoNotTriggerSpike() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.BUY), BigDecimal("0.036"), now + 1000)
        assertFalse(detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), now + 2000))
        assertFalse(detector.isActive())
    }

    @Test
    fun testResetClearsState() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000)
        detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), now + 2000)
        assertTrue(detector.isActive())

        detector.reset()

        assertFalse(detector.isActive())
        assertNull(detector.getSpikeStartTime())
        assertEquals(BigDecimal.ZERO, detector.getTotalAmount())
        assertTrue(detector.getBufferedFills().isEmpty())
    }

    @Test
    fun testTotalAmount() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL, BigDecimal("100")), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL, BigDecimal("200")), BigDecimal("0.036"), now + 1000)
        detector.registerFill(makeOrder(3, SIDE.SELL, BigDecimal("150")), BigDecimal("0.037"), now + 2000)

        assertEquals(0, BigDecimal("450").compareTo(detector.getTotalAmount()))
    }

    @Test
    fun testWeightedAvgPrice() {
        val now = 1000L
        // 100 @ 0.035 = 3.5, 200 @ 0.036 = 7.2, total = 10.7 / 300 = 0.03566667
        detector.registerFill(makeOrder(1, SIDE.SELL, BigDecimal("100")), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL, BigDecimal("200")), BigDecimal("0.036"), now + 1000)

        val expected = BigDecimal("10.7").divide(BigDecimal("300"), 8, java.math.RoundingMode.HALF_UP)
        assertEquals(0, expected.compareTo(detector.getWeightedAvgPrice()))
    }

    @Test
    fun testGetBufferedFills() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000)

        val fills = detector.getBufferedFills()
        assertEquals(2, fills.size)
        assertEquals(BigDecimal("0.035"), fills[0].fillPrice)
        assertEquals(BigDecimal("0.036"), fills[1].fillPrice)
    }

    @Test
    fun testSpikeStartTimeIsFirstFill() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 3000)
        detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), now + 5000)

        assertEquals(now, detector.getSpikeStartTime())
    }

    @Test
    fun testAdditionalFillsAfterSpikeStillActive() {
        val now = 1000L
        detector.registerFill(makeOrder(1, SIDE.SELL), BigDecimal("0.035"), now)
        detector.registerFill(makeOrder(2, SIDE.SELL), BigDecimal("0.036"), now + 1000)
        detector.registerFill(makeOrder(3, SIDE.SELL), BigDecimal("0.037"), now + 2000)
        assertTrue(detector.isActive())

        // Fourth fill should still show spike active
        assertTrue(detector.registerFill(makeOrder(4, SIDE.SELL), BigDecimal("0.038"), now + 3000))
        assertTrue(detector.isActive())
        assertEquals(4, detector.getBufferedFills().size)
    }
}
