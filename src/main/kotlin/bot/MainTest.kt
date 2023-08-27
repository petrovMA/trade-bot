package bot

import bot.trade.libs.readConf
import io.bybit.api.rest.client.ByBitRestApiClient
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")

    val conf = readConf("exchangeConfigs/BYBIT.conf")!!

    val api = conf.getString("api")
    val sec = conf.getString("sec")

// api rest
    ByBitRestApiClient(api, sec).apply {
        val balance = getBalance("CONTRACT")
        println("balance: $balance")

        val newOrder1 = newOrder(
            symbol = "ETHUSDT",
            category = "linear",
            side = "Buy",
            orderType = "Limit",
            qty = "0.01",
            price = "1500"
        )
        println("newOrder1: $newOrder1")

        val newOrder2 = newOrder(
            symbol = "ETHUSDT",
            category = "linear",
            side = "Sell",
            orderType = "Limit",
            qty = "0.01",
            price = "2500"
        )
        println("newOrder2: $newOrder2")

        val orderCancel = orderCancel(category = "linear", symbol = "ETHUSDT", orderId = newOrder1.orderId)
        println("orderCancel: $orderCancel")

        val openOrders = getOpenOrders("linear", "ETHUSDT")
        println("openOrders: $openOrders")

        val orderCancelAll = orderCancelAll("linear", "ETHUSDT")
        println("orderCancelAll: $orderCancelAll")

        val orderBook = getOrderBook("ETHUSDT", "linear")
        println("orderBook: $orderBook")

        val resultKline = getKline("ETHUSDT", "linear", ByBitRestApiClient.INTERVAL.FIVE_MINUTES)
        println("resultKline: $resultKline")

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

//        newOrder(
//            Order(
//                orderId = "",
//                pair = pair,
//                price = BigDecimal(2500),
//                origQty = BigDecimal(0.004),
//                executedQty = BigDecimal(0),
//                side = SIDE.SELL,
//                type = TYPE.LIMIT,
//                status = STATUS.NEW
//            ),
//            true,
//            "%.4f",
//            "%.2f"
//            )
//        val info = getFutureExchangeInfo()
//
//        println(info)
//    }

    /*

    _readMapAndClose(_jsonFactory.createParser(
        "{\"symbol\":\"ETHBUSD\",\"orderId\":7340604491,\"orderListId\":-1,\"clientOrderId\":\"1ih9wSuFwNnQYabAD6fvtj\",\"transactTime\":1690124145296,\"price\":\"2500.00000000\",\"origQty\":\"0.00600000\",\"executedQty\":\"0.00000000\",\"cummulativeQuoteQty\":\"0.00000000\",\"status\":\"NEW\",\"timeInForce\":\"GTC\",\"type\":\"LIMIT\",\"side\":\"SELL\",\"workingTime\":1690124145296,\"fills\":[],\"selfTradePreventionMode\":\"NONE\"}"
), valueType);



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

}
