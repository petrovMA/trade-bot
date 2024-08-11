package bot.trade.exchanges.clients.stream

import GateioStreamingExchange
import bot.trade.exchanges.clients.*
import bot.trade.libs.m
import bot.trade.libs.s
import info.bitrich.xchangestream.binance.BinanceStreamingExchange
import info.bitrich.xchangestream.binancefuture.BinanceFutureStreamingExchange
import info.bitrich.xchangestream.core.ProductSubscription
import info.bitrich.xchangestream.core.StreamingExchangeFactory
import io.bybit.api.websocket.ByBitApiWebSocketListener
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class StreamGateImpl(
    val pair: TradePair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?,
    private val isFuture: Boolean = false,
    private val timeout: Int = 600000
) : Stream() {

    private val log = KotlinLogging.logger {}

    override fun run() {

        val spec = StreamingExchangeFactory.INSTANCE
            .createExchange(GateioStreamingExchange::class.java)
            .defaultExchangeSpecification

        api?.also { spec.apiKey = it }
        sec?.also { spec.secretKey = it }

        val exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec) as GateioStreamingExchange

        val subscription = ProductSubscription.create()
            .addOrderbook(pair.toCurrencyPair())
            .apply { if (api != null && sec != null) addOrders(pair.toCurrencyPair()) }
            .build()

        exchange.connect(subscription).blockingAwait()

        exchange.streamingMarketDataService.getOrderBook(pair.toCurrencyPair()).subscribe(
            { book ->
                log.trace("OrderBook: {}", book)

                val ask = book.asks
                    .filter { o -> o.originalAmount > BigDecimal(0) }
                    .map { Offer(it.limitPrice, it.originalAmount) }
                    .minByOrNull { ask -> ask.price }!!

                val bid = book.bids
                    .filter { o -> o.originalAmount > BigDecimal(0) }
                    .map { Offer(it.limitPrice, it.originalAmount) }
                    .maxByOrNull { it.price }!!

                queue.add(DepthEventOrders(bid = bid, ask = ask))
            },
            { error ->
                log.warn("Trade stream Error:", error)
            }
        )
    }
}
