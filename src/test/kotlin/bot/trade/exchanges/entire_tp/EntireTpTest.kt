package bot.trade.exchanges.entire_tp

import bot.trade.exchanges.*
import bot.trade.exchanges.clients.*
import bot.trade.libs.div8
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import java.math.BigDecimal

class EntireTpTest {

    @Test
    fun testCheckTpDistance() {
        val (algorithmTrader, exchange) = testExchange("entire_tp/checkTpDistanceLongSettings.json")

        exchange.setPosition(
            Position(
                pair = TradePair("ETH_USDT"),
                marketPrice = BigDecimal(2000),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(2000),
                leverage = BigDecimal(1),
                side = "buy"
            )
        )

        algorithmTrader.setup()

        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2001.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2002.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2003.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2004.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2005.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2006.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2007.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2008.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2009.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2010.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2011.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2012.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2013.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2014.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2015.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2025.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2030.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2035.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2040.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2035.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2030.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2025.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2015.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2010.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2005.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1995.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1985.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1980.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1975.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1970.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1965.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1960.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1955.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1950.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1945.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1940.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1935.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1930.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1925.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1920.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1915.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1910.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1905.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1900.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1895.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1890.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1885.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1880.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        algorithmTrader.handle(Trade(1885.toBigDecimal(), 1.toBigDecimal(), 0).toKline())


        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 1885.toBigDecimal(),
                    origQty = 1.0.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1890.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 1885.toBigDecimal(),
                    origQty = 1.0.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 1890.toBigDecimal(),
                    origQty = 1.0.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        assertOrders(emptyMap(), algorithmTrader.orders().second)
    }

    @Test
    fun testCheckMaxTriggerAmountShort() {
        val (algorithmTrader, exchange) = testExchange("entire_tp/checkMaxTriggerAmountShortSettings.json")

        Mapper.asMapObjects<String, Order>(
            file = "entire_tp/checkMaxTriggerAmountShortOrders.json".file(),
            type = object : TypeToken<Map<String?, Order?>?>() {}.type
        ).forEach { (k, v) -> algorithmTrader.orders().second[k] = v }

        exchange.setPosition(
            Position(
                pair = TradePair("ETH_USDT"),
                marketPrice = BigDecimal(2000),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(2000),
                leverage = BigDecimal(1),
                side = "sell"
            )
        )

        algorithmTrader.setup()

        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1995.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1985.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1980.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1975.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1970.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1965.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1960.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1955.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1950.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1955.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1960.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1965.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1970.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1975.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1980.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1985.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1995.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2005.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2010.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2015.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2025.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2030.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2035.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2040.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2045.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2050.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(emptyList(), exchange.orders)

        algorithmTrader.handle(Trade(2030.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 2030.toBigDecimal(),
                    origQty = 0.9.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(2055.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2060.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2057.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2060.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 2030.toBigDecimal(),
                    origQty = 0.9.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 2057.toBigDecimal(),
                    origQty = 0.1.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                ),
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 2060.toBigDecimal(),
                    origQty = 0.8.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )
    }
}