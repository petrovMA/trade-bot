package bot.trade.exchanges.clients.stream

import GateioStreamingExchange
import bot.trade.exchanges.clients.*
import info.bitrich.xchangestream.core.ProductSubscription
import info.bitrich.xchangestream.core.StreamingExchangeFactory
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue

class StreamGateImpl(
    val pairsQueues: Map<TradePair, BlockingQueue<CommonExchangeData>>,
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
            .apply { pairsQueues.forEach { (it, _) -> addOrderbook(it.toCurrencyPair()) } }
            .apply { if (api != null && sec != null) pairsQueues.forEach { (it, _) -> addOrders(it.toCurrencyPair()) } }
            .build()

        exchange.connect(subscription).blockingAwait()

        pairsQueues.forEach { (pair, queue) ->
            exchange.streamingMarketDataService.getOrderBook(pair.toCurrencyPair()).subscribe({ book ->
                log.trace("OrderBook: {}", book)

                val ask = book.asks
                    .map { Offer(it.limitPrice, it.originalAmount) }
                    .minByOrNull { ask -> ask.price }!!

                val bid = book.bids
                    .map { Offer(it.limitPrice, it.originalAmount) }
                    .maxByOrNull { it.price }!!

                queue.add(DepthEventOrders(bid = bid, ask = ask))
            },
                { error -> log.warn("Trade stream Error:", error) }
            )
        }
    }
}
