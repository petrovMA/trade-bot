package bot.trade.exchanges.clients

import bot.trade.libs.UnknownIntervalException
import bot.trade.libs.UnknownOrderStatus
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.gateio.GateioExchange
import org.knowm.xchange.gateio.dto.marketdata.GateioKline
import org.knowm.xchange.gateio.dto.marketdata.GateioKlineInterval
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Wallet
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.gateio.service.GateioAccountService
import org.knowm.xchange.gateio.service.GateioMarketDataService
import org.knowm.xchange.gateio.service.GateioTradeService
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamCurrencyPair
import java.util.concurrent.BlockingQueue


class ClientGate(
    private val api: String? = null,
    private val sec: String? = null,
    private val instance: Exchange = ExchangeFactory.INSTANCE.createExchange(GateioExchange::class.java, api, sec)
) : Client {

    private val tradeService: GateioTradeService = instance.tradeService as GateioTradeService
    private val marketDataService: GateioMarketDataService = instance.marketDataService as GateioMarketDataService
    private val accountService: GateioAccountService = instance.accountService as GateioAccountService
    private val accountInfo: AccountInfo? = if (sec == null) null else accountService.accountInfo
    private val wallets: Map<String, Wallet>? = if (sec == null) null else accountInfo!!.wallets

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        instance.exchangeMetaData.instruments.map { TradePair(it.key.base.currencyCode, it.key.base.currencyCode) }


    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        marketDataService.getKlines(
            pair.toCurrencyPair(),
            asKlineInterval(interval),
            countCandles
        )
            .sortedBy { it.id }
            .map { asCandlestick(it, interval) }

    override fun getOpenOrders(pair: TradePair): List<Order> =
        TODO("09.04.2021 IMPLEMENT IT")
//    tradeService
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

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> = TODO("Not yet implemented")

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

    override fun getBalances(): Map<String, List<Balance>> = wallets?.map {
        it.key to it.value.balances.map { balance ->
            Balance(
                asset = balance.key.currencyCode,
                total = balance.value.total,
                free = balance.value.available,
                locked = balance.value.frozen
            )
        }
    }
        ?.toMap() ?: throw UnsupportedOperationException(
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

    override fun getAssetBalance(asset: String): Map<String, Balance?> = wallets?.map {
        it.key to it.value.getBalance(Currency(asset))?.let { balance ->
            Balance(
                asset = asset,
                total = balance.total,
                free = balance.available,
                locked = balance.frozen
            )
        }
    }?.toMap() ?: throw UnsupportedOperationException(
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
        qty: String,
        price: String
    ): Order {
        TODO("09.04.2021 IMPLEMENT IT")
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
        TODO("09.04.2021 IMPLEMENT IT")
//        try {
//        tradeService.cancelOrder(pair.toCurrencyPair(), orderId.toLong(), null, null)
//        true
//    } catch (e: Exception) {
//        log.warn("Cancel order Error: ", e)
//        false
//    }


    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        TODO("09.04.2021 IMPLEMENT IT")
//        SocketThreadHuobiImpl(
//            pair = pair.toCurrencyPair(),
//            queue = queue,
//            sec = sec,
//            api = api
//        )

    override fun close() {}

    private fun asKlineInterval(interval: INTERVAL): GateioKlineInterval = when (interval) {
        INTERVAL.ONE_MINUTE -> GateioKlineInterval.m1
        INTERVAL.FIVE_MINUTES -> GateioKlineInterval.m5
        INTERVAL.FIFTEEN_MINUTES -> GateioKlineInterval.m15
        INTERVAL.HALF_HOURLY -> GateioKlineInterval.m30
        INTERVAL.HOURLY -> GateioKlineInterval.h1
        INTERVAL.FOUR_HOURLY -> GateioKlineInterval.h4
        INTERVAL.DAILY -> GateioKlineInterval.d1
        INTERVAL.WEEKLY -> GateioKlineInterval.w1
        else -> throw UnknownIntervalException()
    }

    private fun asCandlestick(kline: GateioKline, interval: INTERVAL): Candlestick = Candlestick(
        openTime = kline.id * 1000L,
        closeTime = kline.id * 1000L + interval.toMillsTime() - 1L,
        open = kline.open,
        high = kline.high,
        low = kline.low,
        close = kline.close,
        volume = kline.vol
    )

    override fun toString(): String = "GATE"
}