package bot.telegram.notificator.exchanges.clients.socket

import bot.telegram.notificator.exchanges.clients.*
import io.bitmax.api.rest.client.BitmaxInterval
import io.bitmax.api.websocket.BitMaxApiWebSocketListener
import io.bitmax.api.websocket.messages.responses.WebSocketBar
import io.bitmax.api.websocket.messages.responses.WebSocketDepth
import io.bitmax.api.websocket.messages.responses.WebSocketOrder
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue

class SocketThreadBitmaxImpl(
    val symbol: String,
    var client: BitMaxApiWebSocketListener,
    val interval: INTERVAL,
    private val queue: BlockingQueue<CommonExchangeData>,
    var asks: MutableMap<Double, Double> = mutableMapOf(),
    var bids: MutableMap<Double, Double> = mutableMapOf()
) : SocketThread() {

    private val log = KotlinLogging.logger {}

    private val depthSize: Int = 10

    private var prevCandlestick: Candlestick? = null

    override fun run() {

        try {
            client.setMarketTradesCallback { trades ->
                log.trace("MarketTradesCallback: $trades")
                trades.data.forEach {
                    queue.add(
                        OrderEntry(
                            price = it.p.toDouble(),
                            qty = it.q.toDouble()
                    )
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Socket $symbol connection Exception: ", e)
            e.printStackTrace()
        }

        try {
            client.setBarCallback {
                log.trace("BarCallback: $it")
                if (BitmaxInterval.from(it.data.i).toInterval() == interval) {
                    val candlestick = toCandlestick(it)
                    if (prevCandlestick?.closeTime != candlestick.closeTime) {
                        prevCandlestick = candlestick
                        queue.add(candlestick)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Socket $symbol connection Exception: ", e)
            e.printStackTrace()
        }

        try {
            client.setDepthCallback { depth ->
                log.trace("DepthCallback: $depth")
                updateDepthStateInfo(depth)
                val ask = asks.map { ask -> OrderEntry(ask.key, ask.value) }.minByOrNull { ask -> ask.price }!!
                val bid = bids.map { bid -> OrderEntry(bid.key, bid.value) }.maxByOrNull { bid -> bid.price }!!

//                println("DepthCallback current orderBook: " +
//                        "\na${asks.toList().sortedBy { it.first }.asReversed().joinToString("\na")}" +
//                        "\nb${bids.toList().sortedBy { it.first }.joinToString("\nb")}")

                if (ask.price != 0.0 || bid.price != Double.MAX_VALUE)
                    queue.add(DepthEventOrders(bid = bid, ask = ask))
            }
        } catch (e: Exception) {
            log.error("Socket $symbol connection Exception: ", e)
            e.printStackTrace()
        }

        try {
            client.setOrderCallback {
                log.trace("OrderCallback: $it")
                queue.add(toOrderEvent(it))
            }
        } catch (e: Exception) {
            log.error("Socket $symbol connection Exception: ", e)
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun updateDepthStateInfo(event: WebSocketDepth) {
        var askState = asks

        val maxAskPrice = asks.maxByOrNull { it.key }?.key ?: 0.0

        event.data.asks.forEach {
            if (it[1] == "0")
                askState.remove(it[0].toDouble())
            else
                if (askState.size < depthSize || maxAskPrice > it[0].toDouble()) askState[it[0].toDouble()] = it[1].toDouble()
        }
        if (askState.size > depthSize)
            askState = askState.map { it.key to it.value }
                    .sortedBy { it.first }
                    .subList(0, depthSize)
                    .toMap()
                    .toMutableMap()

        asks = askState


        var bidState = bids

        val minBidPrice = bids.minByOrNull { it.key }?.key ?: Double.MAX_VALUE

        event.data.bids.forEach {
            if (it[1] == "0")
                bidState.remove(it[0].toDouble())
            else
                if (bidState.size < depthSize || minBidPrice < it[0].toDouble()) bidState[it[0].toDouble()] = it[1].toDouble()
        }
        if (bidState.size > depthSize)
            bidState = bidState.map { it.key to it.value }
                    .sortedBy { it.first }
                    .subList(bidState.size - depthSize, bidState.size)
                    .toMap()
                    .toMutableMap()

        bids = bidState
    }

    private fun toCandlestick(event: WebSocketBar) = Candlestick(
            openTime = event.data.ts,
            open = event.data.o.toDouble(),
            close = event.data.c.toDouble(),
            high = event.data.h.toDouble(),
            low = event.data.l.toDouble(),
            volume = event.data.v.toDouble(),
            closeTime = event.data.ts + when (BitmaxInterval.from(event.data.i)) {
                BitmaxInterval.ONE_MINUTE -> 60_000L - 1
                BitmaxInterval.FIVE_MINUTES -> 300_000L - 1
                BitmaxInterval.FIFTEEN_MINUTES -> 900_000L - 1
                BitmaxInterval.HALF_HOURLY -> 1_800_000L - 1
                BitmaxInterval.HOURLY -> 3_600_000L - 1
                BitmaxInterval.TWO_HOURLY -> 3_600_000L * 2 - 1
                BitmaxInterval.FOUR_HOURLY -> 3_600_000L * 4 - 1
                BitmaxInterval.SIX_HOURLY -> 3_600_000L * 6 - 1
                BitmaxInterval.TWELVE_HOURLY -> 3_600_000L * 12 - 1
                BitmaxInterval.DAILY -> 3_600_000L * 24 - 1
                BitmaxInterval.WEEKLY -> 3_600_000L * 7 - 1
                BitmaxInterval.MONTHLY -> 3_600_000L * 30 - 1
            }
    )

    private fun toOrderEvent(event: WebSocketOrder) = Order(
            orderId = event.data.orderId,
            pair = TradePair(event.data.s.replace('/', '_')),
            price = event.data.p.toDouble(),
            origQty = event.data.q.toDouble(),
            executedQty = event.data.q.toDouble(),
            type = when (event.data.ot) {
                "Limit" -> TYPE.LIMIT
                "Market" -> TYPE.MARKET
                else -> TYPE.UNSUPPORTED
            },
            side = when (event.data.sd) {
                "Buy" -> SIDE.BUY
                "Sell" -> SIDE.SELL
                else -> SIDE.UNSUPPORTED
            },
            status = when (event.data.st) {
                "PendingNew" -> STATUS.NEW
                "New" -> STATUS.NEW
                "PartiallyFilled" -> STATUS.PARTIALLY_FILLED
                "Filled" -> STATUS.FILLED
                "Canceled" -> STATUS.CANCELED
                "Rejected" -> STATUS.REJECTED
                else -> STATUS.UNSUPPORTED
            }
    )
}
