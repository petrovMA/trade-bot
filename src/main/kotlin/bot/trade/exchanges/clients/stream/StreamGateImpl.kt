package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import info.bitrich.xchangestream.core.ProductSubscription
import info.bitrich.xchangestream.core.StreamingExchangeFactory
import info.bitrich.xchangestream.gateio.GateioStreamingExchange
import mu.KotlinLogging
import org.knowm.xchange.currency.CurrencyPair
import java.util.concurrent.BlockingQueue

class StreamGateImpl(
    val pair: CurrencyPair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?
) : Stream() {

    private val log = KotlinLogging.logger {}

    override fun run() {
        try {
            val spec = StreamingExchangeFactory.INSTANCE
                .createExchange(GateioStreamingExchange::class.java)
                .defaultExchangeSpecification

            api?.also { spec.apiKey = it }
            sec?.also { spec.secretKey = it }

            val exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec) as GateioStreamingExchange

            // Note: OrderBook and OrderChanges are not available for Gate.io in xchange-stream library
            // - OrderBook: ClassCastException bug in library
            // - OrderChanges: NotYetImplementedException
            // Instead, we use:
            // - getTrades(): market trades for price-based order detection
            // - getUserTrades(): user's own trade executions (requires API keys)
            val subscription = ProductSubscription.create()
                .addTrades(pair)
                .build()

            exchange.connect(subscription).blockingAwait()

            // Subscribe to market trades (primary data source for Grid bot)
            // Grid bot uses trade prices to detect when to update orders
            exchange.streamingMarketDataService.getTrades(pair).subscribe(
                { trade ->
                    queue.add(
                        Trade(
                            price = trade.price,
                            qty = trade.originalAmount,
                            time = trade.timestamp.time
                        )
                    )
                },
                { error ->
                    log.warn("Trade stream Error:", error)
                }
            )

            // Subscribe to user trades (order execution notifications)
            // This tells us when our limit orders are filled
            if (api != null && sec != null) {
                exchange.streamingTradeService.getUserTrades(pair).subscribe(
                    { userTrade ->
                        log.info("Gate.io UserTrade received: orderId={}, pair={}, side={}, price={}, amount={}",
                            userTrade.orderId, userTrade.instrument, userTrade.type, userTrade.price, userTrade.originalAmount)

                        // Convert UserTrade to Order with FILLED status
                        // This triggers the WebSocket FILLED handler in AlgorithmGrid
                        queue.add(
                            Order(
                                orderId = userTrade.orderId,
                                pair = TradePair(userTrade.instrument.toString()),
                                price = userTrade.price,
                                origQty = userTrade.originalAmount,
                                executedQty = userTrade.originalAmount,
                                side = SIDE.valueOf(userTrade.type),
                                type = TYPE.LIMIT,
                                status = STATUS.FILLED,
                                fee = userTrade.feeAmount
                            )
                        )
                    },
                    { error ->
                        log.warn("UserTrade stream Error:", error)
                    }
                )
                log.info("Gate.io stream: Subscribed to trades and user trades for {}", pair)
            } else {
                log.info("Gate.io stream: Using trades only (no API keys for user trades) for {}", pair)
            }

            log.info("Gate.io WebSocket stream started for {}", pair)

        } catch (e: Exception) {
            log.error("Error in Gate.io stream:", e)
            throw e
        }
    }
}