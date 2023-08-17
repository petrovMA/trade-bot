package bot.telegram.notificator.exchanges.clients.stream

import bot.telegram.notificator.exchanges.clients.*
import info.bitrich.xchangestream.binancefuture.BinanceFutureStreamingExchange
import info.bitrich.xchangestream.binancefuture.dto.BinanceFuturesOrderUpdateRaw
import info.bitrich.xchangestream.core.ProductSubscription
import info.bitrich.xchangestream.core.StreamingExchangeFactory
import mu.KotlinLogging
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import org.knowm.xchange.binance.dto.trade.OrderType
import org.knowm.xchange.derivative.FuturesContract
import org.knowm.xchange.instrument.Instrument
import java.util.concurrent.BlockingQueue

class StreamBinanceFuturesImpl(
    val pair: Instrument,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val api: String?,
    private val sec: String?
) : Stream() {

    private val log = KotlinLogging.logger {}

    override fun run() {
        if (pair is FuturesContract) pair
        else throw RuntimeException("Instrument: $pair is not FuturesContract")

        try {
            val specFutures = StreamingExchangeFactory.INSTANCE
                .createExchange(BinanceFutureStreamingExchange::class.java)
                .defaultExchangeSpecification

            api?.also { specFutures.apiKey = it }
            sec?.also { specFutures.secretKey = it }

            val exchangeFutures: BinanceFutureStreamingExchange =
                StreamingExchangeFactory.INSTANCE.createExchange(specFutures) as BinanceFutureStreamingExchange

            val subscription = ProductSubscription.create()
                .addTrades(pair)
                .apply { if (api != null && sec != null) addOrders(pair) }
                .build()

            exchangeFutures.connect(subscription).blockingAwait()

            exchangeFutures.enableLiveSubscription()

            exchangeFutures
                .streamingMarketDataService
                .getKlines(pair, KlineInterval.m5)
                .subscribe({ kline ->
                    queue.add(Candlestick(kline))
                }, { error ->
                    log.warn("Trade stream Error:", error)
                }
                )

            if (api != null && sec != null) {
                exchangeFutures
                    .streamingTradeService
                    .getOrderUpdate(pair)
                    .subscribe({ oc: BinanceFuturesOrderUpdateRaw? ->
                        log.info("Order change: {}", oc)

                        oc?.let { order ->
                            queue.add(
                                Order(
                                    orderId = order.clientOrderId,
                                    pair = TradePair(oc.contract.toString()),
                                    price = order.originalPrice,
                                    origQty = order.originalQuantity,
                                    executedQty = order.orderFilledAccumulatedQuantity,
                                    side = SIDE.valueOf(order.side),
                                    type = when (order.orderType) {
                                        OrderType.MARKET -> TYPE.MARKET
                                        OrderType.LIMIT -> TYPE.LIMIT
                                        else -> TYPE.UNSUPPORTED
                                    },
                                    status = STATUS.valueOf(order.orderStatus),
                                    fee = order.commissionAmount
                                )
                            )
                        }
                    }, { error -> log.warn("Order stream Error:", error) }
                    )

                exchangeFutures
                    .streamingAccountService
                    .balanceChanges
                    .subscribe({ balance ->
                        log.info("Balance change: {}", balance)
                        queue.add(Balance(balance))
                    }, { error -> log.warn("Balance stream Error:", error) }
                    )

                exchangeFutures
                    .streamingAccountService
                    .getPositionChanges(pair)
                    .subscribe({ position ->
                        log.info("Position change: {}", position)
                        queue.add(ExchangePosition(position))
                    }, { error -> log.warn("Position stream Error:", error) }
                    )
            }

        } catch (e: Exception) {
            log.error("Socket $pair connection Exception: ", e)
            e.printStackTrace()
        }
    }
}
