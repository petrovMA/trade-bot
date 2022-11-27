package bot.telegram.notificator

import bot.telegram.notificator.libs.m
import bot.telegram.notificator.libs.readConf
import io.bybit.api.rest.client.ByBitRestApiClient
import io.bybit.api.websocket.ByBitApiWebSocketListener
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")

    val conf = readConf("exchangeConfigs/BYBIT.conf")!!

    val api = conf.getString("api")
    val sec = conf.getString("sec")

/*
// api webSocket
    ByBitApiWebSocketListener(
//        api,
//        sec,
        "wss://stream.bytick.com/realtime",
        500000,
        true,
        WebSocketMsg("subscribe", listOf("order")),
        WebSocketMsg(
            "subscribe",
            listOf(
                "orderBook_200.100ms.BTCUSD",
                "trade",
                "insurance",
                "instrument_info.100ms.BTCUSD",
                "klineV2.1.BTCUSD",
                "liquidation"
            )
        )
    )
        .setOrderBookCallback { println("OrderBook -> $it") }
        .setTradeCallback { println("Trade -> $it") }
        .setKlineCallback { println("Kline -> $it") }
        .setInsuranceCallback { println("Insurance -> $it") }
        .setInstrumentInfoCallback { println("InstrumentInfo -> $it") }
        .setLiquidationCallback { println("Liquidation -> $it") }*/

// api rest
    ByBitRestApiClient(api, sec).apply {
        getOpenOrders("option")
        val resultOrderBook = getOrderBook("ETH-PERP")
        println(resultOrderBook)

        val resultKline = getKline(
            "BTCUSD",
            ByBitRestApiClient.INTERVAL.FIVE_MINUTES,
            (System.currentTimeMillis() / 1000 - 300.m().toMillis() / 1000)
        )
        println(resultKline)

        /*
                val time = getTime()
                println(time.time_now)

                val balance = getBalance()
                println(balance)*/
/*
        val createOrder = orderCreate(
            side = "Buy",
            symbol = "BTCUSD",
            orderType = "Market",
            qty = "10",
            timeInForce = "GoodTillCancel"
        )
        println(createOrder)

        val orderList = getOrderList(
            symbol = "BTCUSD",
            orderStatus = "Filled",
            direction = null,
            limit = null,
            cursor = null
        )
        println(orderList)

        val orderCancel = orderCancel(
            symbol = "BTCUSD",
            orderLinkId = "123456"
        )
        println(orderCancel)
*/
    }
}
