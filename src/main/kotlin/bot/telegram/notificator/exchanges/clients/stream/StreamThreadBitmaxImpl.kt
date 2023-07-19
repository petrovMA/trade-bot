package bot.telegram.notificator.exchanges.clients.stream

import bot.telegram.notificator.exchanges.clients.*
import io.bitmax.api.websocket.BitMaxApiWebSocketListener
import io.bitmax.api.websocket.messages.responses.WebSocketDepth
import io.bitmax.api.websocket.messages.responses.WebSocketOrder
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class StreamThreadBitmaxImpl(
    val pair: TradePair,
    var client: BitMaxApiWebSocketListener,
    val interval: INTERVAL,
    private val queue: BlockingQueue<CommonExchangeData>,
    var asks: MutableMap<BigDecimal, BigDecimal> = mutableMapOf(),
    var bids: MutableMap<BigDecimal, BigDecimal> = mutableMapOf()
) : Stream() {

    private val log = KotlinLogging.logger {}

    private val depthSize: Int = 10

    override fun run() {

        try {
            client.setMarketTradesCallback { trades ->
                log.trace("MarketTradesCallback: $trades")
                trades.data.forEach {
                    queue.add(Trade(price = it.p.toBigDecimal(), qty = it.q.toBigDecimal(), it.ts))
                }
            }
        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }

        try {
            client.setDepthCallback { depth ->
                log.trace("DepthCallback: $depth")
                updateDepthStateInfo(depth)
                val ask = asks.map { ask -> Offer(ask.key, ask.value) }.minByOrNull { ask -> ask.price }!!
                val bid = bids.map { bid -> Offer(bid.key, bid.value) }.maxByOrNull { bid -> bid.price }!!

                if (ask.price != 0.toBigDecimal() || bid.price != Double.MAX_VALUE.toBigDecimal())
                    queue.add(DepthEventOrders(bid = bid, ask = ask))
            }
        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }

        try {
            client.setOrderCallback {
                log.trace("OrderCallback: $it")
                queue.add(toOrderEvent(it))
            }
        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun updateDepthStateInfo(event: WebSocketDepth) {
        var askState = asks

        val maxAskPrice = asks.maxByOrNull { it.key }?.key ?: BigDecimal(0)

        event.data.asks.forEach {
            if (it[1] == "0")
                askState.remove(it[0].toBigDecimal())
            else
                if (askState.size < depthSize || maxAskPrice > it[0].toBigDecimal())
                    askState[it[0].toBigDecimal()] = it[1].toBigDecimal()
        }
        if (askState.size > depthSize)
            askState = askState.map { it.key to it.value }
                    .sortedBy { it.first }
                    .subList(0, depthSize)
                    .toMap()
                    .toMutableMap()

        asks = askState


        var bidState = bids

        val minBidPrice = bids.minByOrNull { it.key }?.key ?: Double.MAX_VALUE.toBigDecimal()

        event.data.bids.forEach {
            if (it[1] == "0")
                bidState.remove(it[0].toBigDecimal())
            else
                if (bidState.size < depthSize || minBidPrice < it[0].toBigDecimal()) bidState[it[0].toBigDecimal()] = it[1].toBigDecimal()
        }
        if (bidState.size > depthSize)
            bidState = bidState.map { it.key to it.value }
                    .sortedBy { it.first }
                    .subList(bidState.size - depthSize, bidState.size)
                    .toMap()
                    .toMutableMap()

        bids = bidState
    }

    private fun toOrderEvent(event: WebSocketOrder) = Order(
            orderId = event.data.orderId,
            pair = TradePair(event.data.s.replace('/', '_')),
            price = event.data.p.toBigDecimal(),
            origQty = event.data.q.toBigDecimal(),
            executedQty = event.data.q.toBigDecimal(),
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
