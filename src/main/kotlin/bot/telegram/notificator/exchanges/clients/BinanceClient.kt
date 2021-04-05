package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.exchanges.clients.socket.SocketThreadBinanceImpl
import bot.telegram.notificator.libs.UnknownOrderStatus
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.binance.dto.marketdata.BinanceKline
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import org.knowm.xchange.binance.service.BinanceAccountService
import org.knowm.xchange.binance.service.BinanceMarketDataService
import org.knowm.xchange.binance.service.BinanceTradeService
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Wallet
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamCurrencyPair
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.BlockingQueue


class BinanceClient(
    private val api: String? = null,
    private val sec: String? = null,
    private val instance: Exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange::class.java, api, sec)
) : Client {

    private val tradeService: BinanceTradeService = instance.tradeService as BinanceTradeService
    private val marketDataService: BinanceMarketDataService = instance.marketDataService as BinanceMarketDataService
    private val accountService: BinanceAccountService = instance.accountService as BinanceAccountService
    private val accountInfo: AccountInfo? = if (sec == null) null else accountService.accountInfo
    private val wallet: Wallet? = if (sec == null) null else accountInfo!!.wallet

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        instance.exchangeSymbols.map { TradePair(it.base.currencyCode, it.counter.currencyCode) }


    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        marketDataService.klines(
            pair.toCurrencyPair(),
            asKlineInterval(interval),
            countCandles,
            null,
            null
        )
            .map { asCandlestick(it) }

    override fun getOpenOrders(pair: TradePair): List<Order> = tradeService
        .getOpenOrders(pair.toCurrencyPair())
        .openOrders
        .map {
            Order(
                price = it.limitPrice,
                pair = pair,
                orderId = it.id,
                origQty = it.originalAmount,
                executedQty = it.cumulativeAmount,
                side = SIDE.valueOf(it.type),
                type = TYPE.LIMIT,
                status = when (it.status) {
                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW

                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED

                    org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
                    org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
                }
            )
        }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> = tradeService
        .openOrders
        .openOrders
        .map {
            Order(
                price = it.limitPrice,
                pair = (it.instrument as CurrencyPair).run { TradePair(base.currencyCode, counter.currencyCode) },
                orderId = it.id,
                origQty = it.originalAmount,
                executedQty = it.cumulativeAmount,
                side = SIDE.valueOf(it.type),
                type = TYPE.LIMIT,
                status = when (it.status) {
                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW

                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED

                    org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
                    org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
                }
            )
        }
        .groupBy { TradePair(it.pair.toString()) }

    override fun getBalances(): List<Balance> = wallet?.balances?.map {
        Balance(
            asset = it.key.currencyCode,
            total = it.value.total,
            free = it.value.available,
            locked = it.value.frozen
        )
    } ?: throw UnsupportedOperationException(
        "This initialization has no API and SECRET keys, " +
                "because of that fun 'getBalances' not supported."
    )

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = marketDataService
        .getOrderBook(CurrencyPair.ETH_BTC, 5)
        .let { book ->
            OrderBook(
                book.asks.map { Offer(it.limitPrice, it.originalAmount) },
                book.bids.map { Offer(it.limitPrice, it.originalAmount) }
            )
        }

    override fun getAssetBalance(asset: String): Balance = wallet?.getBalance(Currency(asset))?.let {
        Balance(
            asset = asset,
            total = it.total,
            free = it.available,
            locked = it.frozen
        )
    } ?: throw UnsupportedOperationException(
        "This initialization has no API and SECRET keys, " +
                "because of that fun 'getAssetBalance' not supported."
    )


    override fun getOrder(pair: TradePair, orderId: String): Order =
        tradeService.getOrder(DefaultQueryOrderParamCurrencyPair(pair.toCurrencyPair(), orderId)).map {
            it as LimitOrder
            Order(
                price = it.limitPrice,
                pair = (it.instrument as CurrencyPair).run { TradePair(base.currencyCode, counter.currencyCode) },
                orderId = it.id,
                origQty = it.originalAmount,
                executedQty = it.cumulativeAmount,
                side = SIDE.valueOf(it.type),
                type = TYPE.LIMIT,
                status = when (it.status) {
                    org.knowm.xchange.dto.Order.OrderStatus.NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> STATUS.NEW
                    org.knowm.xchange.dto.Order.OrderStatus.OPEN -> STATUS.NEW

                    org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> STATUS.CANCELED
                    org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> STATUS.CANCELED

                    org.knowm.xchange.dto.Order.OrderStatus.FILLED -> STATUS.FILLED
                    org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
                }
            )
        }.first()

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        formatCount: String,
        formatPrice: String
    ): Order {
        val typeX = order.side.toType()
        val currPair = CurrencyPair(order.pair.first, order.pair.second)
        val orderId = tradeService.placeLimitOrder(
            LimitOrder(
                typeX,
                String.format(formatCount, order.origQty).toBigDecimal(),
                currPair,
                null,
                Date(),
                String.format(formatPrice, order.price).toBigDecimal()
            )
        )

        return Order(orderId, order.pair, order.price, order.origQty, BigDecimal(0), order.side, order.type, STATUS.NEW)
    }


    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean = try {
        tradeService.cancelOrder(pair.toCurrencyPair(), orderId.toLong(), null, null)
        true
    } catch (e: Exception) {
        log.warn("Cancel order Error: ", e)
        false
    }


    override fun socket(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        SocketThreadBinanceImpl(
            pair = pair.toCurrencyPair(),
            queue = queue,
            sec = sec,
            api = api
        )

    override fun nextEvent() {}
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
        open = kline.openPrice,
        high = kline.highPrice,
        low = kline.lowPrice,
        close = kline.closePrice,
        volume = kline.volume
    )
}
//
//fun AssetBalance.toBalance(): Balance = Balance(
//    asset = asset,
//    total = free.toDouble() + locked.toDouble(),
//    free = free.toDouble(),
//    locked = locked.toDouble()
//)
//
//fun NewOrderResponse.toBinanceOrder(): BinanceOrder = BinanceOrder(
//    this.symbol,
//    this.orderId,
//    this.clientOrderId,
//    this.price,
//    this.origQty,
//    this.executedQty,
//    this.status,
//    this.timeInForce,
//    this.type,
//    this.side
//)
//
//fun INTERVAL.toCandlestickInterval(): CandlestickInterval = when (this) {
//    INTERVAL.ONE_MINUTE -> CandlestickInterval.ONE_MINUTE
//    INTERVAL.THREE_MINUTES -> CandlestickInterval.THREE_MINUTES
//    INTERVAL.FIVE_MINUTES -> CandlestickInterval.FIVE_MINUTES
//    INTERVAL.FIFTEEN_MINUTES -> CandlestickInterval.FIFTEEN_MINUTES
//    INTERVAL.HALF_HOURLY -> CandlestickInterval.HALF_HOURLY
//    INTERVAL.HOURLY -> CandlestickInterval.HOURLY
//    INTERVAL.TWO_HOURLY -> CandlestickInterval.TWO_HOURLY
//    INTERVAL.FOUR_HOURLY -> CandlestickInterval.FOUR_HOURLY
//    INTERVAL.SIX_HOURLY -> CandlestickInterval.SIX_HOURLY
//    INTERVAL.EIGHT_HOURLY -> CandlestickInterval.EIGHT_HOURLY
//    INTERVAL.TWELVE_HOURLY -> CandlestickInterval.TWELVE_HOURLY
//    INTERVAL.DAILY -> CandlestickInterval.DAILY
//    INTERVAL.THREE_DAILY -> CandlestickInterval.THREE_DAILY
//    INTERVAL.WEEKLY -> CandlestickInterval.WEEKLY
//    INTERVAL.MONTHLY -> CandlestickInterval.MONTHLY
//}
//
//fun BinanceOrder.toOrder(pair: TradePair): Order = Order(
//    pair = pair,
//    orderId = this.clientOrderId,
//    price = this.price.toDouble(),
//    origQty = this.origQty.toDouble(),
//    executedQty = this.executedQty.toDouble(),
//    status = when (this.status) {
//        OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//        OrderStatus.FILLED -> STATUS.FILLED
//        OrderStatus.CANCELED -> STATUS.CANCELED
//        OrderStatus.NEW -> STATUS.NEW
//        OrderStatus.REJECTED -> STATUS.REJECTED
//        else -> STATUS.UNSUPPORTED
//    },
//    type = when (this.type) {
//        OrderType.LIMIT -> TYPE.LIMIT
//        OrderType.MARKET -> TYPE.MARKET
//        else -> TYPE.UNSUPPORTED
//    },
//    side = when (this.side) {
//        OrderSide.SELL -> SIDE.SELL
//        OrderSide.BUY -> SIDE.BUY
//        else -> SIDE.UNSUPPORTED
//    }
//)
//
//fun BinanceCandlestick.toCandlestick(): Candlestick = Candlestick(
//    openTime = openTime.toLong(),
//    closeTime = closeTime.toLong(),
//    open = open.toDouble(),
//    high = high.toDouble(),
//    low = low.toDouble(),
//    close = close.toDouble(),
//    volume = volume.toDouble()
//)
//
//fun BinanceOrderBook.toOrderBook(): OrderBook = OrderBook(
//    asks = asks.map { OrderEntry(price = it.price.toDouble(), qty = it.qty.toDouble()) },
//    bids = bids.map { OrderEntry(price = it.price.toDouble(), qty = it.qty.toDouble()) }
//)
//
//fun File.toCandlestick(): List<Candlestick> = readObjectFromFile(this, ArrayList::class.java)
//        .map { it as BinanceCandlestick }
//        .map { it.toCandlestick() }