package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import bot.trade.libs.m
import bot.trade.libs.s
import io.bybit.api.websocket.ByBitApiWebSocketListener
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class StreamByBitFuturesImpl(
    val pair: TradePair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?,
    private val isFuture: Boolean = false,
    private val timeout: Int = 600000
) : Stream() {

    private var publicStream: ByBitApiWebSocketListener? = null
    private var privateStream: ByBitApiWebSocketListener? = null

    override fun run() {
        val publicUrl = if (isFuture) "wss://stream.bybit.com/v5/public/linear"
        else "wss://stream.bybit.com/v5/public/spot"

        var currBid: Offer? = null
        var currAsk: Offer? = null

        publicStream = ByBitApiWebSocketListener(
            url = publicUrl,
            timeout = timeout,
            keepConnection = true,
            pingTimeInterval = 50.s(),
            reconnectIfNoMessagesDuring = 2.m(),
            //WebSocketMsg("subscribe", listOf("kline.5.${pair.first}${pair.second}"))
            WebSocketMsg(
                "subscribe",
                listOf(/*"publicTrade.${pair.first}${pair.second}", */"orderbook.1.${pair.first}${pair.second}")
            )
        )

        /*publicStream?.setKlineCallback {
            it.data
                .map { kline -> Candlestick(kline) }
                .sortedBy { kline -> kline.closeTime }
                .forEach { kline -> queue.add(kline) }
        }*/

        /*publicStream?.setTradeCallback {
            it.data
                .map { trade -> Trade(trade.price.toBigDecimal(), trade.volume.toBigDecimal(), trade.timestamp) }
                .sortedBy { trade -> trade.time }
                .forEach { trade -> queue.add(trade) }
        }*/



        publicStream?.setOrderBookCallback {

            val newAsk = if (it.data.a.isNotEmpty())
                it.data.a.map { o -> Offer(o.first(), o.last()) }
                    .filter { o -> o.qty > BigDecimal(0) }
                    .minByOrNull { o -> o.price }!!
            else null

            currAsk = newAsk ?: currAsk

            val newBid = if (it.data.b.isNotEmpty())
                it.data.b.map { o -> Offer(o.first(), o.last()) }
                    .filter { o -> o.qty > BigDecimal(0) }
                    .maxByOrNull { o -> o.price }!!
            else null

            currBid = newBid ?: currBid

            if (currAsk != null && currBid != null) queue.add(DepthEventOrders(currAsk!!, currBid!!))
        }

        if (api != null && sec != null) {
            privateStream = ByBitApiWebSocketListener(
                api = api,
                sec = sec,
                url = "wss://stream.bybit.com/v5/private?max_alive_time=1m",
                timeout = timeout,
                keepConnection = true,
                pingTimeInterval = 30.s(),
                WebSocketMsg("subscribe", listOf("order", "position"))
            )

//            privateStream?.setOrderCallback { queue.addAll(it.data.map { order -> Order(order) }) }
//            if (isFuture) privateStream?.setPositionCallback { queue.addAll(it.data.map { position -> Position(position) }) }
        }
    }

    override fun interrupt() {
        publicStream?.disconnect()
        privateStream?.disconnect()
        super.interrupt()
    }
}
