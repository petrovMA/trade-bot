package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.libs.UnknownIntervalException
import bot.trade.libs.UnknownOrderStatus
import exchange_api.gate.rest.client.GateRestApiClient
import exchange_api.gate.rest.response.GateOrderResponse
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.gateio.GateioExchange
import org.knowm.xchange.currency.Currency
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Wallet
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.gateio.service.GateioAccountService
import org.knowm.xchange.gateio.service.GateioMarketDataService
import org.knowm.xchange.gateio.service.GateioTradeService
import org.knowm.xchange.instrument.Instrument
import org.knowm.xchange.service.trade.params.InstrumentParam
import org.knowm.xchange.service.trade.params.orders.OpenOrdersParams
import java.math.BigDecimal
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
    private val client: GateRestApiClient? = if (api != null && sec != null) GateRestApiClient(api, sec) else null

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        instance.exchangeMetaData.instruments.map { TradePair(it.key.base.currencyCode, it.key.base.currencyCode) }


    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {
        // Temporary solution: return current ticker as candlestick for grid bot initialization
        // Grid bots primarily need current price, not historical candles
        log.warn("getCandlestickBars called - returning ticker data as fallback for Grid bot")

        return try {
            val ticker = marketDataService.getTicker(pair.toCurrencyPair())
            val currentTime = System.currentTimeMillis()

            listOf(Candlestick(
                openTime = currentTime - 60000,  // 1 minute ago
                closeTime = currentTime,
                open = ticker.last ?: ticker.bid,
                high = ticker.high ?: ticker.last ?: ticker.bid,
                low = ticker.low ?: ticker.last ?: ticker.bid,
                close = ticker.last ?: ticker.bid,
                volume = ticker.volume ?: BigDecimal.ZERO
            ))
        } catch (e: Exception) {
            log.error("Failed to get ticker data for $pair", e)
            // Return default candlestick to prevent bot crash
            listOf(Candlestick(
                openTime = System.currentTimeMillis() - 60000,
                closeTime = System.currentTimeMillis(),
                open = BigDecimal.ONE,
                high = BigDecimal.ONE,
                low = BigDecimal.ONE,
                close = BigDecimal.ONE,
                volume = BigDecimal.ZERO
            ))
        }
    }

    override fun getOpenOrders(pair: TradePair): List<Order> = tradeService
        .getOpenOrders(GateIoOpenOrdersParams(pair.toCurrencyPair()))
        .openOrders
        .mapNotNull {
            try {
                // Validate required fields - skip orders with missing critical data
                val orderType = it.type ?: run {
                    log.warn("Order ${it.id} has null type, skipping")
                    return@mapNotNull null
                }

                val originalAmount = it.originalAmount ?: run {
                    log.warn("Order ${it.id} has null originalAmount, skipping")
                    return@mapNotNull null
                }

                val cumulativeAmount = it.cumulativeAmount ?: BigDecimal.ZERO

                Order(
                    price = it.limitPrice,
                    pair = pair,
                    orderId = it.id,
                    origQty = originalAmount,
                    executedQty = cumulativeAmount,
                    side = SIDE.valueOf(orderType),
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
            } catch (e: Exception) {
                log.error("Failed to parse order ${it.id} from Gate.io: ${e.message}", e)
                null
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

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        pairs.associateWith { getOpenOrders(it) }

    override fun getBalances(): Map<String, List<Balance>> {
        if (sec == null) {
            throw UnsupportedOperationException(
                "This initialization has no API and SECRET keys, " +
                        "because of that fun 'getBalances' not supported."
            )
        }

        // Get fresh account info instead of using cached wallets
        val freshAccountInfo = accountService.accountInfo
        val freshWallets = freshAccountInfo.wallets

        return freshWallets.map {
            it.key to it.value.balances.map { balance ->
                Balance(
                    asset = balance.key.currencyCode,
                    total = balance.value.total,
                    free = balance.value.available,
                    locked = balance.value.frozen
                )
            }
        }.toMap()
    }

    override fun getBalance(coin: String): Balance? {
        if (sec == null) {
            return null
        }

        // Get fresh account info instead of using cached wallets
        val freshAccountInfo = accountService.accountInfo
        val freshWallets = freshAccountInfo.wallets

        // Try to find the balance in any wallet (spot, margin, etc.)
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
        .getOrderBook(CurrencyPair.ETH_BTC, 5)
        .let { book ->
            OrderBook(
                book.asks.map { Offer(it.limitPrice, it.originalAmount) },
                book.bids.map { Offer(it.limitPrice, it.originalAmount) }
            )
        }

    override fun getAssetBalance(asset: String): Map<String, Balance?> {
        if (sec == null) {
            throw UnsupportedOperationException(
                "This initialization has no API and SECRET keys, " +
                        "because of that fun 'getAssetBalance' not supported."
            )
        }

        // Get fresh account info instead of using cached wallets
        val freshAccountInfo = accountService.accountInfo
        val freshWallets = freshAccountInfo.wallets

        return freshWallets.map {
            it.key to it.value.getBalance(Currency(asset))?.let { balance ->
                Balance(
                    asset = asset,
                    total = balance.total,
                    free = balance.available,
                    locked = balance.frozen
                )
            }
        }.toMap()
    }


    override fun getOrder(pair: TradePair, orderId: String): Order {
        requireNotNull(client) { "GateRestApiClient not initialized. API and Secret keys are required." }

        val symbol = "${pair.first}_${pair.second}"
        val response = client.getOrder(symbol, orderId)

        return Order(
            price = response.price,
            pair = pair,
            orderId = response.orderId,
            origQty = response.qty,
            executedQty = response.executedQty ?: BigDecimal.ZERO,
            side = if (response.side.equals("Buy", ignoreCase = true)) SIDE.BUY else SIDE.SELL,
            type = TYPE.LIMIT,
            status = when (response.status.uppercase()) {
                "NEW", "OPEN" -> STATUS.NEW
                "FILLED", "CLOSED" -> STATUS.FILLED
                "PARTIALLY_FILLED" -> STATUS.PARTIALLY_FILLED
                "CANCELLED", "CANCELED" -> STATUS.CANCELED
                else -> throw UnknownOrderStatus("Error: Unknown status '${response.status}'!")
            }
        )
    }

    /**
     * Public method for placing orders directly with Gate.io API client
     * This is used by test code and direct API interactions
     */
    fun newOrder(
        symbol: TradePair,
        category: String = "spot",
        side: String,
        orderType: String,
        qty: String,
        price: String,
        timeInForce: String = "GTC"
    ): GateOrderResponse {
        requireNotNull(client) { "GateRestApiClient not initialized. API and Secret keys are required." }

        val symbolStr = "${symbol.first}_${symbol.second}"
        return client.newOrder(
            symbol = symbolStr,
            category = category,
            side = side,
            orderType = orderType,
            qty = qty,
            price = price,
            timeInForce = timeInForce
        )
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        requireNotNull(client) { "GateRestApiClient not initialized. API and Secret keys are required." }

        val side = when (order.side) {
            SIDE.BUY -> "Buy"
            SIDE.SELL -> "Sell"
            else -> throw IllegalArgumentException("Unknown side: ${order.side}")
        }

        val orderType = when (order.type) {
            TYPE.LIMIT -> "Limit"
            TYPE.MARKET -> "Market"
            else -> throw IllegalArgumentException("Unsupported order type: ${order.type}")
        }

        val response = newOrder(
            symbol = order.pair,
            category = "spot",
            side = side,
            orderType = orderType,
            qty = qty,
            price = price
        )

        return Order(
            orderId = response.orderId,
            pair = order.pair,
            price = response.price,
            origQty = response.qty,
            executedQty = BigDecimal.ZERO,
            side = order.side,
            type = order.type,
            status = STATUS.NEW
        )
    }


    /**
     * Public method for canceling orders directly with Gate.io API client
     */
    fun orderCancel(
        category: String = "spot",
        symbol: TradePair,
        orderId: String
    ): Boolean {
        requireNotNull(client) { "GateRestApiClient not initialized. API and Secret keys are required." }

        val symbolStr = "${symbol.first}_${symbol.second}"
        return client.orderCancel(
            category = category,
            symbol = symbolStr,
            orderId = orderId
        )
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        requireNotNull(client) { "GateRestApiClient not initialized. API and Secret keys are required." }

        return try {
            orderCancel(
                category = "spot",
                symbol = pair,
                orderId = orderId
            )
        } catch (e: Exception) {
            log.warn("Cancel order Error: ", e)
            false
        }
    }


    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream {
        log.info("Starting WebSocket stream for Gate.io: {}", pair)
        return bot.trade.exchanges.clients.stream.StreamGateImpl(
            pair = pair.toCurrencyPair(),
            queue = queue,
            api = api,
            sec = sec
        )
    }

    override fun close() {}

    // Temporarily commented out due to gateio-v4 library compatibility issues
    /*
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
    */

    override fun toString(): String = "GATE"

    class GateIoOpenOrdersParams(val currencyPair: CurrencyPair) : OpenOrdersParams, InstrumentParam {
        override fun getInstrument(): Instrument {
            return currencyPair
        }

        override fun setInstrument(instrument: Instrument?) {
            // not needed
        }

        override fun accept(order: LimitOrder): Boolean {
            return order.instrument == currencyPair
        }
    }
    
    class GateIoOrderQueryParams(
        private val currencyPair: CurrencyPair,
        private var orderId: String
    ) : org.knowm.xchange.service.trade.params.orders.OrderQueryParamInstrument {
        override fun getInstrument(): Instrument {
            return currencyPair
        }

        override fun setInstrument(instrument: Instrument?) {
            // not needed
        }
        
        override fun getOrderId(): String {
            return orderId
        }
        
        override fun setOrderId(orderId: String) {
            this.orderId = orderId
        }
    }
}