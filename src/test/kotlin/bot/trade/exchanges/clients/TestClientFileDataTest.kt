package bot.trade.exchanges.clients

import bot.trade.exchanges.assertPosition
import bot.trade.libs.deserialize
import bot.trade.libs.m
import bot.trade.libs.toZonedTime
import org.junit.jupiter.api.Test
import utils.resourceFile
import java.math.BigDecimal
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible


class TestClientFileDataTest {

    @Test
    fun newOrderLong() {
        val emulateClient = TestClientFileData(
            fee = BigDecimal(0.1),
            fileData = resourceFile<TestClientFileDataTest>("test_klines.csv"),
            to = System.currentTimeMillis().toZonedTime(),
            from = System.currentTimeMillis().toZonedTime(),
            params = resourceFile<TestClientFileDataTest>("emulateSettings.json").readText()
                .deserialize<BotEmulateParams>()
        )

        // Finding the property by name
        val property: KMutableProperty1<TestClientFileData, Any> =
            (TestClientFileData::class.memberProperties.find { it.name == "candlestick" }
                    as KMutableProperty1<TestClientFileData, Any>?)!!

        // Making the property accessible
        property.isAccessible = true

        property.set(emulateClient, Candlestick("1;3000;3000;3000;3000;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.LONG,
            order = Order(
                orderId = "1",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(1),
                executedQty = BigDecimal(0),
                side = SIDE.BUY,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(1),
                breakEvenPrice = BigDecimal(3000),
                marketPrice = BigDecimal(3000),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3000),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "BUY"
            ),
            emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "BUY" }
        )

        property.set(emulateClient, Candlestick("1;3200;3200;3200;3200;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.LONG,
            order = Order(
                orderId = "2",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(1),
                executedQty = BigDecimal(0),
                side = SIDE.BUY,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(2),
                breakEvenPrice = BigDecimal(3100),
                marketPrice = BigDecimal(3200),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3000),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "BUY"
            ), emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "BUY" }
        )

        property.set(emulateClient, Candlestick("1;3000;3000;3000;3000;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.LONG,
            order = Order(
                orderId = "3",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(2),
                executedQty = BigDecimal(0),
                side = SIDE.BUY,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(4),
                breakEvenPrice = BigDecimal(3050),
                marketPrice = BigDecimal(3000),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3000),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "BUY"
            ), emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "BUY" }
        )

        property.set(emulateClient, Candlestick("1;3300;3300;3300;3300;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.LONG,
            order = Order(
                orderId = "4",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(4),
                executedQty = BigDecimal(0),
                side = SIDE.SELL,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        val profit = emulateClient.emulate()

        println(profit)
    }

    @Test
    fun newOrderShort() {
        val emulateClient = TestClientFileData(
            //fee = BigDecimal(0.1),
            fileData = resourceFile<TestClientFileDataTest>("test_klines.csv"),
            to = System.currentTimeMillis().toZonedTime(),
            from = System.currentTimeMillis().toZonedTime(),
            params = resourceFile<TestClientFileDataTest>("emulateSettings.json").readText()
                .deserialize<BotEmulateParams>()
        )

        // Finding the property by name
        val property: KMutableProperty1<TestClientFileData, Any> =
            (TestClientFileData::class.memberProperties.find { it.name == "candlestick" }
                    as KMutableProperty1<TestClientFileData, Any>?)!!

        // Making the property accessible
        property.isAccessible = true

        property.set(emulateClient, Candlestick("1;3600;3600;3600;3600;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.SHORT,
            order = Order(
                orderId = "1",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(1),
                executedQty = BigDecimal(0),
                side = SIDE.SELL,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(1),
                breakEvenPrice = BigDecimal(3600),
                marketPrice = BigDecimal(3600),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3600),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "SELL"
            ),
            emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "SELL" }
        )

        property.set(emulateClient, Candlestick("1;3200;3200;3200;3200;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.SHORT,
            order = Order(
                orderId = "2",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(1),
                executedQty = BigDecimal(0),
                side = SIDE.SELL,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(2),
                breakEvenPrice = BigDecimal(3400),
                marketPrice = BigDecimal(3200),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3600),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "SELL"
            ),
            emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "SELL" }
        )

        property.set(emulateClient, Candlestick("1;3100;3100;3100;3100;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.SHORT,
            order = Order(
                orderId = "3",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(1),
                executedQty = BigDecimal(0),
                side = SIDE.SELL,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        assertPosition(
            Position(
                pair = TradePair("TEST_PAIR"),
                size = BigDecimal(3),
                breakEvenPrice = BigDecimal(3300),
                marketPrice = BigDecimal(3100),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(3600),
                leverage = BigDecimal(0),
                liqPrice = BigDecimal(0),
                side = "SELL"
            ),
            emulateClient.getPositions(TradePair("TEST_PAIR")).find { it.side == "SELL" }
        )

        property.set(emulateClient, Candlestick("1;3200;3200;3200;3200;20".split(';'), 1.m()))

        emulateClient.newOrder(
            positionSide = DIRECTION.SHORT,
            order = Order(
                orderId = "4",
                pair = TradePair("TEST_PAIR"),
                price = null,
                origQty = BigDecimal(3),
                executedQty = BigDecimal(0),
                side = SIDE.BUY,
                type = TYPE.MARKET,
                status = STATUS.NEW,
            ),
            isStaticUpdate = false,
            qty = "",
            price = "",
        )

        val profit = emulateClient.emulate()

        println(profit)
    }
}