package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamBinanceImpl
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.UnknownOrderStatus
import bot.trade.libs.UnsupportedOrderTypeException
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.binance.BinanceExchange
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import org.knowm.xchange.binance.dto.trade.OrderSide
import org.knowm.xchange.binance.service.BinanceAccountService
import org.knowm.xchange.binance.service.BinanceMarketDataService
import org.knowm.xchange.binance.service.BinanceTradeService
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Wallet
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.dto.trade.MarketOrder
import org.knowm.xchange.instrument.Instrument
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamCurrencyPair
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.BlockingQueue


open class ClientBinance(
    private val api: String? = null,
    private val sec: String? = null,
    private val instance: Exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange::class.java, api, sec)
) : Client {

    val tradeService: BinanceTradeService = instance.tradeService as BinanceTradeService
    val marketDataService: BinanceMarketDataService = instance.marketDataService as BinanceMarketDataService
    val accountService: BinanceAccountService = instance.accountService as BinanceAccountService
    val accountInfo: AccountInfo? = if (sec == null) null else accountService.accountInfo
    val wallets: Map<String, Wallet>? = if (sec == null) null else accountInfo!!.wallets

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        instance.exchangeMetaData.instruments.map { TradePair(it.key.base.currencyCode, it.key.base.currencyCode) }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        getCandlestickBars(pair, interval, countCandles, null, null)

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> =
        marketDataService.klines(
            pair.toCurrencyPair(),
            asKlineInterval(interval),
            countCandles,
            start,
            end
        ).map { Candlestick(it) }

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
        .getOrderBook((CurrencyPair.ETH_BTC as Instrument), 5)
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


    override fun getOrder(pair: TradePair, orderId: String): Order {
        return tradeService.getOrder(DefaultQueryOrderParamCurrencyPair(pair.toCurrencyPair(), orderId)).map {
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
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        val typeX = order.side.toType()
        val currPair = CurrencyPair(order.pair.first, order.pair.second)
        val orderId = when (order.type) {
            TYPE.LIMIT -> tradeService.placeLimitOrder(
                LimitOrder(
                    typeX,
                    qty.toBigDecimal(),
                    currPair,
                    null,
                    Date(),
                    price.toBigDecimal()
                )
            )

            TYPE.MARKET -> tradeService.placeMarketOrder(
                MarketOrder(
                    typeX,
                    qty.toBigDecimal(),
                    currPair
                )
            )

            else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${order.type}'!")
        }

        return Order(orderId, order.pair, order.price, order.origQty, BigDecimal(0), order.side, order.type, STATUS.NEW)


//        return tradeService.newOrder(
//            order.pair.toCurrencyPair(),
//            toBinanceSide(order.side),
//            OrderType.LIMIT,
//            TimeInForce.GTC,
//            String.format(formatCount, order.origQty).toBigDecimal(),
//            String.format(formatPrice, order.price).toBigDecimal(),
//            "${System.currentTimeMillis()}_${order.pair}",
//            null,
//            null
//        ).let {
//            Order(
//                price = it.price,
//                pair = order.pair,
//                orderId = "${it.orderId}|${it.clientOrderId}",
//                origQty = it.origQty,
//                executedQty = it.executedQty,
//                side = fromBinanceSide(it.side),
//                type = TYPE.LIMIT,
//                status = when (it.status) {
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.NEW -> STATUS.NEW
//
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.CANCELED -> STATUS.CANCELED
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.PENDING_CANCEL -> STATUS.CANCELED
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.REJECTED -> STATUS.CANCELED
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.EXPIRED -> STATUS.CANCELED
//
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.FILLED -> STATUS.FILLED
//                    org.knowm.xchange.binance.dto.trade.OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//                    else -> throw UnknownOrderStatus("Error: Unknown status '${it.status}'!")
//                }
//            )
//        }
    }


    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean = try {
        tradeService.cancelOrder(orderId)
        true
    } catch (e: Exception) {
        log.warn("Cancel order Error: ", e)
        false
    }


    override fun stream(pairs: List<TradePair>, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream =
        StreamBinanceImpl(
            pair = pairs.first().toCurrencyPair(),
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

    override fun toString(): String = "BINANCE"

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
}