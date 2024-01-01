package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import bot.trade.libs.m
import bot.trade.libs.s
import io.bybit.api.websocket.ByBitApiWebSocketListener
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue

class StreamByBitFuturesImpl(
    val pair: TradePair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?,
    private val isFuture: Boolean = true,
    private val timeout: Int = 600000
) : Stream() {

    private val log = KotlinLogging.logger {}

    override fun run() {
        val publicUrl = if (isFuture) "wss://stream.bybit.com/v5/public/linear"
        else "wss://stream.bybit.com/v5/public/spot"

        try {
            val publicStream = ByBitApiWebSocketListener(
                url = publicUrl,
                timeout = timeout,
                keepConnection = true,
                pingTimeInterval = 50.s(),
                reconnectIfNoMessagesDuring = 2.m(),
//                WebSocketMsg("subscribe", listOf("publicTrade.${pair.first}${pair.second}"))
                WebSocketMsg("subscribe", listOf("kline.5.${pair.first}${pair.second}"))
            )

            /*publicStream.setKlineCallback {
                it.data
                    .map { kline -> Candlestick(kline) }
                    .sortedBy { kline -> kline.closeTime }
                    .forEach { kline -> queue.add(kline) }
            }*/
            /*publicStream.setTradeCallback {
                queue.addAll(it.data.map { trade ->
                    Trade(
                        price = trade.price.toBigDecimal(),
                        qty = trade.volume.toBigDecimal(),
                        time = trade.timestamp
                    )
                })
            }*/

            if (api != null && sec != null) {
                val privateStream = ByBitApiWebSocketListener(
                    api = api,
                    sec = sec,
                    url = "wss://stream.bybit.com/v5/private?max_alive_time=1m",
                    timeout = timeout,
                    keepConnection = true,
                    pingTimeInterval = 30.s(),
                    WebSocketMsg("subscribe", listOf(/*"order", */"position"))
                )

                privateStream.setOrderCallback { queue.addAll(it.data.map { order -> Order(order) }) }
                privateStream.setPositionCallback {
                    println("PositionCallback: $it")
                    queue.addAll(it.data.map { position -> Position(position) })
                }

//                privateStream
//                    .streamingTradeService
//                    .getOrderUpdate(pair)
//                    .subscribe({ oc: BinanceFuturesOrderUpdateRaw? ->
//                        log.info("Order change: {}", oc)
//
//                        oc?.let { order ->
//                            queue.add(
//                                Order(
//                                    orderId = order.clientOrderId,
//                                    pair = TradePair(oc.contract.toString()),
//                                    price = order.originalPrice,
//                                    origQty = order.originalQuantity,
//                                    executedQty = order.orderFilledAccumulatedQuantity,
//                                    side = SIDE.valueOf(order.side),
//                                    type = when (order.orderType) {
//                                        OrderType.MARKET -> TYPE.MARKET
//                                        OrderType.LIMIT -> TYPE.LIMIT
//                                        else -> TYPE.UNSUPPORTED
//                                    },
//                                    status = STATUS.valueOf(order.orderStatus),
//                                    fee = order.commissionAmount
//                                )
//                            )
//                        }
//                    }, { error -> log.warn("Order stream Error:", error) }
//                    )
//
//                privateStream
//                    .streamingAccountService
//                    .balanceChanges
//                    .subscribe({ balance ->
//                        log.info("Balance change: {}", balance)
//                        queue.add(Balance(balance))
//                    }, { error -> log.warn("Balance stream Error:", error) }
//                    )
//
//                privateStream
//                    .streamingAccountService
//                    .getPositionChanges(pair)
//                    .subscribe({ position ->
//                        log.info("Position change: {}", position)
//                        queue.add(ExchangePosition(position))
//                    }, { error -> log.warn("Position stream Error:", error) }
//                    )
            }

        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }
    }
}
