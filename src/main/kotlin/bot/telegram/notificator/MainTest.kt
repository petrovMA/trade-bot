package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.clients.stream.StreamBinanceFuturesImpl
import bot.telegram.notificator.exchanges.clients.stream.StreamBinanceImpl
import bot.telegram.notificator.libs.m
import bot.telegram.notificator.libs.poll
import bot.telegram.notificator.libs.readConf
import bot.telegram.notificator.libs.s
import bot.telegram.notificator.libs.*
import io.bybit.api.rest.client.ByBitRestApiClient
import io.bybit.api.websocket.ByBitApiWebSocketListener
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator
import org.knowm.xchange.derivative.FuturesContract
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")

//    val conf = readConf("exchangeConfigs/BYBIT.conf")!!
    val conf = readConf("exchangeConfigs/BINANCE_FUTURES.conf")!!

    val api = conf.getString("api")
    val sec = conf.getString("sec")

//    (newClient(ExchangeEnum.BINANCE_FUTURES, api, sec) as ClientBinanceFutures).run {
//    (newClient(ExchangeEnum.BINANCE, api, sec) as ClientBinance).run {

//        val info1 = marketDataService.getExchangeInfo()
//        println(info1)
//        val info = getFutureExchangeInfo()
//        println(info)

        val pair = TradePair("ETH_USDT")
        val queue = LinkedBlockingDeque<CommonExchangeData>()

        StreamBinanceFuturesImpl(
//        StreamBinanceImpl(
            FuturesContract(pair.toCurrencyPair(), "PERPETUAL"),
//            pair.toCurrencyPair(),
            queue,
            api,
            sec,
            true
        ).apply {
            start()
            do {
                val msg = queue.poll(30.s())
                log.info(msg.toString())
            } while (true)
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

// api rest
//    ByBitRestApiClient(api, sec).apply {
//        getOpenOrders("option")
//        val resultOrderBook = getOrderBook("ETH-PERP")
//        println(resultOrderBook)
//
//        val resultKline = getKline(
//            "BTCUSD",
//            ByBitRestApiClient.INTERVAL.FIVE_MINUTES,
//            (System.currentTimeMillis() / 1000 - 300.m().toMillis() / 1000)
//        )
//        println(resultKline)

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
