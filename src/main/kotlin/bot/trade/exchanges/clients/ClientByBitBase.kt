package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.StreamByBitFuturesImpl
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.UnsupportedOrderTypeException
import exchange_api.bybit.rest.client.ByBitRestApiClient
import exchange_api.bybit.rest.response.OpenOrders
import mu.KotlinLogging
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import java.math.BigDecimal
import java.net.URLDecoder
import java.util.concurrent.BlockingQueue

abstract class ClientByBitBase(protected val api: String? = null, protected val sec: String? = null) : Client {

    protected val client = if (api != null && sec != null) ByBitRestApiClient(api, sec)
    else throw IllegalArgumentException("api and sec must be not null")

    protected val log = KotlinLogging.logger {}

    protected abstract fun getCategory(): String
    protected abstract fun getPositionIdx(positionSide: DIRECTION?): Int

    override fun getAllPairs(): List<TradePair> =
        TODO("getAllPairs NOT IMPLEMENTED")

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        getCandlestickBars(pair, interval, countCandles, null, null)

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> =
        client.getKline(
            symbol = pair.first + pair.second,
            category = getCategory(),
            interval = interval.toByBitInterval(),
            limit = countCandles.toLong(),
            start = start,
            end = end
        )
            .list
            .map { asCandlestick(it, interval) }

    override fun getOpenOrders(pair: TradePair): List<Order> {
        val result: ArrayList<OpenOrders.Result.ByBitOrder> = ArrayList()
        var cursor: String? = null
        var list: List<OpenOrders.Result.ByBitOrder>

        do {
            val response = client.getOpenOrders(
                category = getCategory(),
                symbol = pair.first + pair.second,
                cursor = cursor,
                limit = 50
            )

            list = response.list
            cursor = response.nextPageCursor?.let {
                log.debug("Raw cursor from ByBit: '$it'")
                val normalized = normalizeCursor(it)
                log.debug("Normalized cursor: '$normalized'")
                normalized
            }
            result += list
        } while (cursor.isNullOrBlank().not() || list.isNotEmpty())

        return result
            .filter {
                it.symbol.startsWith(pair.first)
                        && it.symbol.endsWith(pair.second)
                        && it.symbol.length == (pair.first + pair.second).length
            }
            .map {
                Order(
                    price = it.price.toBigDecimal(),
                    pair = TradePair(it.symbol.substring(0, 3), it.symbol.substring(3)),
                    orderId = it.orderId,
                    origQty = it.qty.toBigDecimal(),
                    executedQty = it.cumExecQty.toBigDecimal(),
                    side = SIDE.valueOf(it.side.uppercase()),
                    type = TYPE.LIMIT,
                    status = mapOrderStatus(it.orderStatus)
                )
            }
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        client.getOpenOrders(category = getCategory())
            .list
            .filter {
                val pair = try {
                    TradePair(it.symbol.substring(0, 3), it.symbol.substring(3))
                } catch (t: Throwable) {
                    log.warn("Can't convert to pair ${it.symbol} error: ${t.message}")
                    null
                }
                pairs.contains(pair)
            }
            .map {
                Order(
                    price = it.price.toBigDecimal(),
                    pair = TradePair(it.symbol.substring(0, 3), it.symbol.substring(3)),
                    orderId = it.orderId,
                    origQty = it.qty.toBigDecimal(),
                    executedQty = it.cumExecQty.toBigDecimal(),
                    side = SIDE.valueOf(it.side),
                    type = TYPE.LIMIT,
                    status = mapOrderStatus(it.orderStatus)
                )
            }
            .groupBy { it.pair }

    override fun getBalances(): Map<String, List<Balance>> = mapOf(client.getBalance("UNIFIED").let {
        it.accountType to it.balance.map { balance ->
            Balance(
                asset = balance.coin,
                total = balance.walletBalance.toBigDecimal(),
                free = balance.transferBalance.toBigDecimal(),
                locked = balance.walletBalance.toBigDecimal() - balance.transferBalance.toBigDecimal()
            )
        }
    })

    override fun getBalance(coin: String): Balance = client.getBalance("UNIFIED", coin = coin).let {
        val balances = it.balance.map { balance ->
            Balance(
                asset = balance.coin,
                total = balance.walletBalance.toBigDecimal(),
                free = balance.transferBalance.toBigDecimal(),
                locked = balance.walletBalance.toBigDecimal() - balance.transferBalance.toBigDecimal()
            )
        }

        balances.first()
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook =
        TODO("getOrderBook NOT IMPLEMENTED")

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        mapOf(client.getBalance(accountType = "UNIFIED", coin = asset).let {
            it.accountType to it.balance.find { b -> b.coin == asset }?.run {
                Balance(
                    asset = coin,
                    total = walletBalance.toBigDecimal(),
                    free = transferBalance.toBigDecimal(),
                    locked = walletBalance.toBigDecimal() - transferBalance.toBigDecimal()
                )
            }
        })

    override fun getOrder(pair: TradePair, orderId: String): Order? = client.getOpenOrders(
        category = getCategory(),
        orderId = orderId
    )
        .list
        .firstOrNull()
        ?.run {
            Order(
                price = price.toBigDecimal(),
                pair = pair,
                orderId = orderId,
                origQty = qty.toBigDecimal(),
                executedQty = cumExecQty.toBigDecimal(),
                side = SIDE.fromString(side),
                type = TYPE.LIMIT,
                status = mapOrderStatus(orderStatus)
            )
        } ?: client.getOrdersHistory(
        category = getCategory(),
        orderId = orderId
    )
        .list
        .firstOrNull()
        ?.run {
            Order(
                price = price.toBigDecimal(),
                pair = pair,
                orderId = orderId,
                origQty = qty.toBigDecimal(),
                executedQty = cumExecQty.toBigDecimal(),
                side = SIDE.fromString(side),
                type = TYPE.LIMIT,
                status = mapOrderStatus(orderStatus)
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
        val resp = client.newOrder(
            symbol = order.pair.first + order.pair.second,
            category = getCategory(),
            side = when (order.side) {
                SIDE.BUY -> "Buy"
                SIDE.SELL -> "Sell"
                else -> throw UnknownOrderSide("Error, side: $this")
            },
            orderType = when (order.type) {
                TYPE.LIMIT -> "Limit"
                TYPE.MARKET -> "Market"
                else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${order.type}'!")
            },
            qty = qty,
            price = price,
            positionIdx = getPositionIdx(positionSide),
            reduceOnly = isReduceOnly
        )
        return Order(
            resp.orderId,
            order.pair,
            order.price,
            order.origQty,
            BigDecimal(0),
            order.side,
            order.type,
            STATUS.NEW
        )
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        try {
            client.orderCancel(
                symbol = pair.first + pair.second,
                orderId = orderId,
                category = getCategory()
            )
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Can't cancel order with ID = $orderId", t)
            return false
        }
    }

    override fun close() {}

    protected fun asCandlestick(candlestick: List<String>, interval: INTERVAL): Candlestick = Candlestick(
        openTime = candlestick[0].toLong(),
        closeTime = candlestick[0].toLong() + interval.toMillsTime(),
        open = candlestick[1].toBigDecimal(),
        high = candlestick[2].toBigDecimal(),
        low = candlestick[3].toBigDecimal(),
        close = candlestick[4].toBigDecimal(),
        volume = candlestick[5].toBigDecimal()
    )

    protected fun INTERVAL.toByBitInterval(): ByBitRestApiClient.INTERVAL = when (this) {
        INTERVAL.ONE_MINUTE -> ByBitRestApiClient.INTERVAL.ONE_MINUTE
        INTERVAL.THREE_MINUTES -> ByBitRestApiClient.INTERVAL.THREE_MINUTES
        INTERVAL.FIVE_MINUTES -> ByBitRestApiClient.INTERVAL.FIVE_MINUTES
        INTERVAL.FIFTEEN_MINUTES -> ByBitRestApiClient.INTERVAL.FIFTEEN_MINUTES
        INTERVAL.HALF_HOURLY -> ByBitRestApiClient.INTERVAL.HALF_HOURLY
        INTERVAL.HOURLY -> ByBitRestApiClient.INTERVAL.HOURLY
        INTERVAL.TWO_HOURLY -> ByBitRestApiClient.INTERVAL.TWO_HOURLY
        INTERVAL.FOUR_HOURLY -> ByBitRestApiClient.INTERVAL.FOUR_HOURLY
        INTERVAL.SIX_HOURLY -> ByBitRestApiClient.INTERVAL.SIX_HOURLY
        INTERVAL.EIGHT_HOURLY -> throw RuntimeException("ByBit not support 8h interval")
        INTERVAL.TWELVE_HOURLY -> ByBitRestApiClient.INTERVAL.TWELVE_HOURLY
        INTERVAL.DAILY -> ByBitRestApiClient.INTERVAL.DAILY
        INTERVAL.THREE_DAILY -> throw RuntimeException("ByBit not support 3d interval")
        INTERVAL.WEEKLY -> ByBitRestApiClient.INTERVAL.WEEKLY
        INTERVAL.MONTHLY -> ByBitRestApiClient.INTERVAL.MONTHLY
    }

    protected fun normalizeCursor(cursor: String): String {
        return try {
            // Check if the cursor contains URL-encoded characters
            if (cursor.contains("%")) {
                // Decode once to ensure we have the raw format
                val decoded = URLDecoder.decode(cursor, "UTF-8")
                log.debug("Cursor normalized: $cursor -> $decoded")
                decoded
            } else {
                // Already in raw format
                cursor
            }
        } catch (e: Exception) {
            log.warn("Failed to normalize cursor: $cursor, using as-is", e)
            cursor
        }
    }

    protected fun mapOrderStatus(orderStatus: String): STATUS = when (orderStatus) {
        "Created" -> STATUS.NEW
        "New" -> STATUS.NEW
        "Rejected" -> STATUS.REJECTED
        "PartiallyFilled" -> STATUS.PARTIALLY_FILLED
        "PartiallyFilledCanceled" -> STATUS.CANCELED
        "Filled" -> STATUS.FILLED
        "Cancelled" -> STATUS.CANCELED
        "Untriggered" -> STATUS.UNSUPPORTED
        "Triggered" -> STATUS.UNSUPPORTED
        "Deactivated" -> STATUS.UNSUPPORTED
        "Active" -> STATUS.UNSUPPORTED
        else -> STATUS.UNSUPPORTED
    }
}
