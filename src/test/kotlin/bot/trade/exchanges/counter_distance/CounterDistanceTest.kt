package bot.trade.exchanges.counter_distance

import bot.trade.exchanges.*
import bot.trade.exchanges.clients.*
import bot.trade.libs.div8
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import java.math.BigDecimal

class CounterDistanceTest {

    @Test
    fun testCounterDistanceShort() {
        val (algorithmTrader, exchange) = testExchange("counter_distance/shortSettings.json")

        exchange.setPosition(
            Position(
                pair = TradePair("ETH_USDT"),
                marketPrice = BigDecimal(2000),
                unrealisedPnl = BigDecimal(0),
                realisedPnl = BigDecimal(0),
                entryPrice = BigDecimal(2000),
                leverage = BigDecimal(1),
                side = "Sell"
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
        algorithmTrader.handle(Trade(2016.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2017.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2018.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2019.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2021.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2022.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(listOf(), exchange.orders)
        assertOrders("counter_distance/ShortOrdersExpected_1.json".file(), algorithmTrader.orders().third)

        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = "2020.00".toBigDecimal(),
                    origQty = 0.5.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.SELL,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )
        algorithmTrader.handle(Trade(2010.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2005.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1998.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1997.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1996.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1995.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1994.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1993.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1992.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1991.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders("counter_distance/ShortOrdersExpected_2.json".file(), algorithmTrader.orders().third)
    }

    @Test
    fun testCounterDistanceLong() {
        val (algorithmTrader, exchange) = testExchange("counter_distance/longSettings.json")

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
        algorithmTrader.handle(Trade(2016.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2017.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2018.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2019.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2021.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2022.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2020.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders("counter_distance/LongOrdersExpected_1.json".file(), algorithmTrader.orders().second)

        algorithmTrader.handle(Trade(2010.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2005.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(2000.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1998.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1997.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1996.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1995.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1994.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1993.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1992.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1991.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1989.toBigDecimal(), 1.toBigDecimal(), 0).toKline())
        algorithmTrader.handle(Trade(1990.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(listOf(), exchange.orders)
        assertOrders("counter_distance/LongOrdersExpected_2.json".file(), algorithmTrader.orders().second)

        algorithmTrader.handle(Trade(1991.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders(
            listOf(
                Order(
                    orderId = "",
                    pair = TradePair("ETH", "USDT"),
                    price = 1991.toBigDecimal(),
                    origQty = 1.4.toBigDecimal(),
                    executedQty = 0.toBigDecimal(),
                    side = SIDE.BUY,
                    type = TYPE.MARKET,
                    status = STATUS.NEW
                )
            ),
            exchange.orders
        )

        algorithmTrader.handle(Trade(1992.toBigDecimal(), 1.toBigDecimal(), 0).toKline())

        assertOrders("counter_distance/LongOrdersExpected_3.json".file(), algorithmTrader.orders().second)
    }
}