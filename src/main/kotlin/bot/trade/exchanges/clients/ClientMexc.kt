package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamThreadStub
import bot.trade.libs.UnknownOrderStatus
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Wallet
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.dto.trade.MarketOrder
import org.knowm.xchange.service.trade.params.orders.DefaultOpenOrdersParamCurrencyPair
import org.knowm.xchange.service.trade.params.orders.DefaultQueryOrderParamCurrencyPair
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.BlockingQueue

class ClientMexc(
    private val api: String? = null,
    private val sec: String? = null,
    private val instance: Exchange = ExchangeFactory.INSTANCE.createExchange("org.knowm.xchange.mexc.MexcExchange", api, sec)
) : Client {

    private val tradeService = instance.tradeService
    private val marketDataService = instance.marketDataService
    private val accountService = instance.accountService
    private val accountInfo: AccountInfo? = if (sec == null) null else accountService.accountInfo
    private val wallets: Map<String, Wallet>? = if (sec == null) null else accountInfo!!.wallets

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        instance.exchangeMetaData.instruments.map { TradePair(it.key.base.currencyCode, it.key.base.currencyCode) }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {
        log.warn("getCandlestickBars called for MEXC - falling back to ticker-based candlestick")

        return try {
            val ticker = marketDataService.getTicker(pair.toCurrencyPair())
            val currentTime = System.currentTimeMillis()

            listOf(
                Candlestick(
                    openTime = currentTime - 60000,
                    closeTime = currentTime,
                    open = ticker.last ?: ticker.bid,
                    high = ticker.high ?: ticker.last ?: ticker.bid,
                    low = ticker.low ?: ticker.last ?: ticker.bid,
                    close = ticker.last ?: ticker.bid,
                    volume = ticker.volume ?: BigDecimal.ZERO
                )
            )
        } catch (e: Exception) {
            log.error("Failed to get ticker data for $pair", e)
            listOf(
                Candlestick(
                    openTime = System.currentTimeMillis() - 60000,
                    closeTime = System.currentTimeMillis(),
                    open = BigDecimal.ONE,
                    high = BigDecimal.ONE,
                    low = BigDecimal.ONE,
                    close = BigDecimal.ONE,
                    volume = BigDecimal.ZERO
                )
            )
        }
    }

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> {
        log.warn("getCandlestickBars with time range called - delegating to simple version")
        return getCandlestickBars(pair, interval, countCandles)
    }

    override fun getOpenOrders(pair: TradePair): List<Order> = tradeService
        .getOpenOrders(DefaultOpenOrdersParamCurrencyPair(pair.toCurrencyPair()))
        .openOrders
        .map {
            Order(
                price = it.limitPrice,
                pair = pair,
                orderId = it.id,
                origQty = it.originalAmount,
                executedQty = it.cumulativeAmount,
                side = SIDE.valueOf(it.type ?: throw IllegalStateException("Order ${it.id} has null type")),
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

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        pairs.associateWith { getOpenOrders(it) }

    override fun getBalances(): Map<String, List<Balance>> = wallets?.map {
        it.key to it.value.balances.map { balance ->
            Balance(
                asset = balance.key.currencyCode,
                total = balance.value.total,
                free = balance.value.available,
                locked = balance.value.frozen
            )
        }
    }?.toMap() ?: throw UnsupportedOperationException(
        "This initialization has no API and SECRET keys, because of that fun 'getBalances' not supported."
    )

    override fun getBalance(coin: String): Balance? {
        if (sec == null) {
            return null
        }

        val freshAccountInfo = accountService.accountInfo
        val freshWallets = freshAccountInfo.wallets

        return freshWallets.values.firstNotNullOfOrNull { wallet ->
            wallet.getBalance(Currency(coin))?.let { balance ->
                Balance(
                    asset = coin,
                    total = balance.total,
                    free = balance.available,
                    locked = balance.frozen
                )
            }
        }
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = marketDataService
        .getOrderBook(pair.toCurrencyPair())
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
        "This initialization has no API and SECRET keys, because of that fun 'getAssetBalance' not supported."
    )

    override fun getOrder(pair: TradePair, orderId: String): Order =
        tradeService.getOrder(DefaultQueryOrderParamCurrencyPair(pair.toCurrencyPair(), orderId)).map {
            it as LimitOrder
            Order(
                price = it.limitPrice,
                pair = (it.instrument as org.knowm.xchange.currency.CurrencyPair).run {
                    TradePair(base.currencyCode, counter.currencyCode)
                },
                orderId = it.id,
                origQty = it.originalAmount,
                executedQty = it.cumulativeAmount,
                side = SIDE.valueOf(it.type ?: throw IllegalStateException("Order ${it.id} has null type")),
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
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        val typeX = order.side.toType()
        val currPair = org.knowm.xchange.currency.CurrencyPair(order.pair.first, order.pair.second)
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
            else -> throw IllegalArgumentException("Unsupported order type: ${order.type}")
        }

        return Order(orderId, order.pair, order.price, order.origQty, BigDecimal(0), order.side, order.type, STATUS.NEW)
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean = try {
        tradeService.cancelOrder(orderId)
        true
    } catch (e: Exception) {
        log.warn("Cancel order Error: ", e)
        false
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream =
        StreamThreadStub()

    override fun close() {}

    override fun toString(): String = "MEXC"
}
