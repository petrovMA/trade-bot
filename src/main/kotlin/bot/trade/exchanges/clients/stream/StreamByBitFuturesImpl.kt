package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
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
                WebSocketMsg("subscribe", listOf("publicTrade.${pair.first}${pair.second}"))
            )

            publicStream.setKlineCallback { queue.addAll(it.data.map { kline -> Candlestick(kline) }) }
            publicStream.setTradeCallback {
                queue.addAll(it.data.map { trade ->
                    Trade(
                        price = trade.price.toBigDecimal(),
                        qty = trade.volume.toBigDecimal(),
                        time = trade.timestamp
                    )
                })
            }

            if (api != null && sec != null) {
                val privateStream = ByBitApiWebSocketListener(
                    api = api,
                    sec = sec,
                    url = "wss://stream.bybit.com/v5/private",
                    timeout = timeout,
                    keepConnection = true,
                    WebSocketMsg("subscribe", listOf("order"))
                )

                privateStream.setOrderCallback {
                    queue.addAll(it.data.map { order ->
                        Order(
                            orderId = order.orderId,
                            pair = order.symbol.run { TradePair(take(3), drop(3)) },
                            price = order.price.toBigDecimal(),
                            origQty = order.qty.toBigDecimal(),
                            executedQty = order.cumExecQty.toBigDecimal(),
                            side = SIDE.valueOf(order.side.uppercase()),
                            type = TYPE.valueOf(order.orderType.uppercase()),
                            status = when (order.orderStatus) {
                                "Created" -> STATUS.NEW
                                "New" -> STATUS.NEW
                                "Rejected" -> STATUS.REJECTED
                                "PartiallyFilled" -> STATUS.PARTIALLY_FILLED
                                "PartiallyFilledCanceled" -> STATUS.CANCELED
                                "Filled" -> STATUS.FILLED
                                "Cancelled" -> STATUS.CANCELED
                                "Untriggered" -> STATUS.UNSUPPORTED
                                "Triggered" -> STATUS.UNSUPPORTED
                                "Deactivated" -> STATUS.UNSUPPORTED
                                "Active" -> STATUS.UNSUPPORTED
                                else -> STATUS.UNSUPPORTED
                            },
                            fee = order.cumExecFee.toBigDecimal()
                        )
                    })
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
