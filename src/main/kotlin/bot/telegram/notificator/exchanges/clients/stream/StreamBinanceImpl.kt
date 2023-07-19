package bot.telegram.notificator.exchanges.clients.stream

import bot.telegram.notificator.exchanges.clients.*
import info.bitrich.xchangestream.binance.BinanceStreamingExchange
import info.bitrich.xchangestream.core.ProductSubscription
import info.bitrich.xchangestream.core.StreamingExchangeFactory
import mu.KotlinLogging
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.dto.trade.MarketOrder
import java.util.concurrent.BlockingQueue

class StreamBinanceImpl(
    val pair: CurrencyPair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?,
    private val isFuture: Boolean = false
) : Stream() {

    private val log = KotlinLogging.logger {}

    override fun run() {

        try {

            val spec = StreamingExchangeFactory.INSTANCE
                .createExchange(BinanceStreamingExchange::class.java)
                .defaultExchangeSpecification

            api?.also { spec.apiKey = it }
            sec?.also { spec.secretKey = it }

//            val specFutures = StreamingExchangeFactory.INSTANCE
//                .createExchange(BinanceFutureStreamingExchange::class.java)
//                .defaultExchangeSpecification
//
//            api?.also { specFutures.apiKey = it }
//            sec?.also { specFutures.secretKey = it }

            val exchange = StreamingExchangeFactory.INSTANCE.createExchange(spec) as BinanceStreamingExchange
//            val exchangeFutures =
//                StreamingExchangeFactory.INSTANCE.createExchange(specFutures) as BinanceFutureStreamingExchange

            val subscription = ProductSubscription.create()
                .addTrades(TradePair("ETH_USDT").toCurrencyPair())
                .addOrderbook(TradePair("ETH_USDT").toCurrencyPair())
                .apply { if (api != null && sec != null) addOrders(TradePair("ETH_USDT").toCurrencyPair()) }
                .build()

            exchange.connect(subscription).blockingAwait()

//            exchangeFutures.connect(
//                ProductSubscription
//                    .create()
//                    .apply { if (api != null && sec != null) addOrders(FuturesContract(pair, null)) }
//                    .build()
//            ).blockingAwait()

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

            /*exchange.streamingMarketDataService.getOrderBook(pair).subscribe(
                { book ->
                    log.trace("OrderBook: $book")

                    val ask = book.asks
                        .map { Offer(it.limitPrice, it.originalAmount) }
                        .minByOrNull { ask -> ask.price }!!

                    val bid = book.bids
                        .map { Offer(it.limitPrice, it.originalAmount) }
                        .maxByOrNull { it.price }!!

                    queue.add(DepthEventOrders(bid = bid, ask = ask))
                },
                { error ->
                    log.warn("OrderBook stream Error:", error)
                }
            )*/

            if (api != null && sec != null) {
                exchange.streamingTradeService.getOrderChanges(isFuture).subscribe(
                    { oc: Order? ->
                        log.info("Order change: {}", oc)

                        oc?.let { order ->
                            when (order) {
                                is LimitOrder -> queue.add(
                                    Order(
                                        orderId = order.id,
                                        pair = TradePair(oc.instrument.toString()),
                                        price = order.limitPrice,
                                        origQty = order.originalAmount,
                                        executedQty = order.cumulativeAmount,
                                        side = SIDE.valueOf(order.type),
                                        type = TYPE.LIMIT,
                                        status = STATUS.valueOf(order.status)
                                    )
                                )

                                is MarketOrder -> queue.add(
                                    Order(
                                        orderId = order.id,
                                        pair = TradePair(oc.instrument.toString()),
                                        price = order.averagePrice,
                                        origQty = order.originalAmount,
                                        executedQty = order.cumulativeAmount,
                                        side = SIDE.valueOf(order.type),
                                        type = TYPE.MARKET,
                                        status = STATUS.valueOf(order.status),
                                        fee = order.fee
                                    )
                                )
                            }
                        }
                    }, { error ->
                        log.warn("Order stream Error:", error)
                    }
                )

//                exchangeFutures.streamingTradeService.getOrderChanges(true).subscribe(
//                    { oc: Order? ->
//                        log.info("Futures Order change: {}", oc)
//                    }, { error ->
//                        log.warn("Order stream Error:", error)
//                    }
//                )
            }

        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }
    }
}
