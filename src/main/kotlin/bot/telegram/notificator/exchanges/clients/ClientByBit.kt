package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.exchanges.clients.stream.StreamBinanceImpl
import bot.telegram.notificator.libs.UnknownOrderSide
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.knowm.xchange.binance.dto.marketdata.BinanceKline
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import org.knowm.xchange.binance.dto.trade.OrderSide
import java.util.concurrent.BlockingQueue


class ClientByBit(
    private val api: String? = null,
    private val sec: String? = null,
//    private val instance: Exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange::class.java, api, sec)
) : Client {

//    private val tradeService: BinanceTradeService = instance.tradeService as BinanceTradeService
//    private val marketDataService: BinanceMarketDataService = instance.marketDataService as BinanceMarketDataService
//    private val accountService: BinanceAccountService = instance.accountService as BinanceAccountService
//    private val accountInfo: AccountInfo? = if (sec == null) null else accountService.accountInfo
//    private val wallet: Wallet? = if (sec == null) null else accountInfo!!.wallet
//    private val timestampFactory: BinanceTimestampFactory

    val client = OkHttpClient().newBuilder().build()

    private val log = KotlinLogging.logger {}

    // todo STUB 'timestampFactory' begin
    private val serverInterval: Long = 15000

    override fun getAllPairs(): List<TradePair> =
        TODO("getAllPairs NOT IMPLEMENTED")
//        instance.exchangeSymbols.map { TradePair(it.base.currencyCode, it.counter.currencyCode) }


    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {
        getCandles()
        TODO("getCandlestickBars NOT IMPLEMENTED")
    }
//        marketDataService.klines(
//            pair.toCurrencyPair(),
//            asKlineInterval(interval),
//            countCandles,
//            null,
//            null
//        ).map { asCandlestick(it) }


    override fun getOpenOrders(pair: TradePair): List<Order> =
        TODO("getOpenOrders NOT IMPLEMENTED")

//        tradeService
//        .getOpenOrders(pair.toCurrencyPair())
//        .openOrders
//        .map {
//            Order(
//                price = it.limitPrice,
//                pair = pair,
//                orderId = it.id,
//                origQty = it.originalAmount,
//                executedQty = it.cumulativeAmount,
//                side = SIDE.valueOf(it.type),
//                type = TYPE.LIMIT,
//                status = when (it.status) {
//                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW
//
//                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED
//
//                    org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
//                    org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
//                }
//            )
//        }


    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        TODO("getAllOpenOrders NOT IMPLEMENTED")

//        tradeService
//        .openOrders
//        .openOrders
//        .map {
//            Order(
//                price = it.limitPrice,
//                pair = (it.instrument as CurrencyPair).run { TradePair(base.currencyCode, counter.currencyCode) },
//                orderId = it.id,
//                origQty = it.originalAmount,
//                executedQty = it.cumulativeAmount,
//                side = SIDE.valueOf(it.type),
//                type = TYPE.LIMIT,
//                status = when (it.status) {
//                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW
//
//                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED
//
//                    org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
//                    org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
//                }
//            )
//        }
//        .groupBy { TradePair(it.pair.toString()) }


    override fun getBalances(): Map<String, List<Balance>>? =
        TODO("getBalances NOT IMPLEMENTED")

//    wallet?.balances?.map {
//        Balance(
//            asset = it.key.currencyCode,
//            total = it.value.total,
//            free = it.value.available,
//            locked = it.value.frozen
//        )
//    } ?: throw UnsupportedOperationException(
//        "This initialization has no API and SECRET keys, " +
//                "because of that fun 'getBalances' not supported."
//    )

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook =
        TODO("getOrderBook NOT IMPLEMENTED")

//        marketDataService
//        .getOrderBook(CurrencyPair.ETH_BTC, 5)
//        .let { book ->
//            OrderBook(
//                book.asks.map { Offer(it.limitPrice, it.originalAmount) },
//                book.bids.map { Offer(it.limitPrice, it.originalAmount) }
//            )
//        }

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        TODO("getAssetBalance NOT IMPLEMENTED")

//        wallet?.getBalance(Currency(asset))?.let {
//        Balance(
//            asset = asset,
//            total = it.total,
//            free = it.available,
//            locked = it.frozen
//        )
//    } ?: throw UnsupportedOperationException(
//        "This initialization has no API and SECRET keys, " +
//                "because of that fun 'getAssetBalance' not supported."
//    )


    override fun getOrder(pair: TradePair, orderId: String): Order {
        TODO("getOrder NOT IMPLEMENTED")

//        println((exchange.timestampFactory as BinanceTimestampFactory).deltaServerTime())
//        return tradeService.getOrder(DefaultQueryOrderParamCurrencyPair(pair.toCurrencyPair(), orderId)).map {
//            it as LimitOrder
//            Order(
//                price = it.limitPrice,
//                pair = (it.instrument as CurrencyPair).run { TradePair(base.currencyCode, counter.currencyCode) },
//                orderId = it.id,
//                origQty = it.originalAmount,
//                executedQty = it.cumulativeAmount,
//                side = SIDE.valueOf(it.type),
//                type = TYPE.LIMIT,
//                status = when (it.status) {
//                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
//                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW
//
//                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
//                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED
//
//                        org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
//                        org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//                        else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
//                    }
//                )
//            }.first()
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        formatCount: String,
        formatPrice: String
    ): Order {
        TODO("newOrder NOT IMPLEMENTED")

//        val typeX = order.side.toType()
//        val currPair = CurrencyPair(order.pair.first, order.pair.second)
//        val orderId = tradeService.placeLimitOrder(
//            LimitOrder(
//                typeX,
//                String.format(formatCount, order.origQty).toBigDecimal(),
//                currPair,
//                null,
//                Date(),
//                String.format(formatPrice, order.price).toBigDecimal()
//            )
//        )
//
//        return Order(orderId, order.pair, order.price, order.origQty, BigDecimal(0), order.side, order.type, STATUS.NEW)
    }


    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean =
        TODO("cancelOrder NOT IMPLEMENTED")

        /*try {
        tradeService.cancelOrder(pair.toCurrencyPair(), orderId.toLong(), null, null)


//        val ids = orderId.split('|')
//
//        if (ids.size > 1)
//            tradeService.cancelOrder(pair.toCurrencyPair(), ids[0].toLong(), ids[1], null)
//        else
//            tradeService.cancelOrder(pair.toCurrencyPair(), orderId.toLong(), null, null)

        true
    } catch (e: Exception) {
        log.warn("Cancel order Error: ", e)
        false
    }*/


    override fun socket(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamBinanceImpl(
            pair = pair.toCurrencyPair(),
            queue = queue,
            sec = sec,
            api = api
        )

    override fun close() {}

    private fun asKlineInterval(interval: INTERVAL): KlineInterval = when (interval) {
        INTERVAL.ONE_MINUTE -> KlineInterval.m1
        INTERVAL.THREE_MINUTES -> KlineInterval.m3
        INTERVAL.FIVE_MINUTES -> KlineInterval.m5
        INTERVAL.FIFTEEN_MINUTES -> KlineInterval.m15
        INTERVAL.HALF_HOURLY -> KlineInterval.m30
        INTERVAL.HOURLY -> KlineInterval.h1
        INTERVAL.TWO_HOURLY -> KlineInterval.h2
        INTERVAL.FOUR_HOURLY -> KlineInterval.h4
        INTERVAL.SIX_HOURLY -> KlineInterval.h6
        INTERVAL.EIGHT_HOURLY -> KlineInterval.h8
        INTERVAL.TWELVE_HOURLY -> KlineInterval.h12
        INTERVAL.DAILY -> KlineInterval.d1
        INTERVAL.THREE_DAILY -> KlineInterval.d3
        INTERVAL.WEEKLY -> KlineInterval.w1
        INTERVAL.MONTHLY -> KlineInterval.M1
    }

    private fun asCandlestick(kline: BinanceKline): Candlestick = Candlestick(
        openTime = kline.openTime,
        closeTime = kline.closeTime,
        open = kline.open,
        high = kline.high,
        low = kline.low,
        close = kline.close,
        volume = kline.volume
    )

    override fun toString(): String = "BYBIT"

    private fun fromBinanceSide(side: OrderSide): SIDE = when (side) {
        OrderSide.BUY -> SIDE.BUY
        OrderSide.SELL -> SIDE.SELL
        else -> SIDE.UNSUPPORTED
    }

    private fun toBinanceSide(side: SIDE): OrderSide = when (side) {
        SIDE.BUY -> OrderSide.BUY
        SIDE.SELL -> OrderSide.SELL
        else -> throw UnknownOrderSide("Unsupported Order side!")
    }


    /*private*/ fun getCandles() {
        val request: Request = Request.Builder()
            .url("https://api.bybit.com/v2/public/mark-price-kline?interval=1&from=1544156750&symbol=ETHUSD")
            .method("GET", null)
            .build()
        var response: Response = client.newCall(request).execute()
        println(response)
    }
}