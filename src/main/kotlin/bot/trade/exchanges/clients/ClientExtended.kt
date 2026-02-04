package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamExtendedImpl
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.UnsupportedOrderTypeException
import exchange_api.extended.rest.client.ExtendedRestApiClient
import exchange_api.extended.rest.response.ExtendedCreateOrderRequest
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class ClientExtended(
    private val apiKey: String,
    private val starkKey: String? = null
) : ClientFutures {

    private val restClient = ExtendedRestApiClient(apiKey)
    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        restClient.getMarkets()
            .filter { it.status.equals("ACTIVE", ignoreCase = true) }
            .map { TradePair(it.baseAsset, it.quoteAsset) }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        getCandlestickBars(pair, interval, countCandles, null, null)

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> =
        restClient.getCandles(
            market = toMarket(pair),
            interval = toExtendedInterval(interval),
            limit = countCandles,
            endTime = end
        ).mapNotNull { candle ->
            val openTime = candle.timestamp ?: return@mapNotNull null
            Candlestick(
                openTime = openTime,
                closeTime = openTime + interval.toMillsTime(),
                open = candle.open?.toBigDecimal() ?: return@mapNotNull null,
                high = candle.high?.toBigDecimal() ?: return@mapNotNull null,
                low = candle.low?.toBigDecimal() ?: return@mapNotNull null,
                close = candle.close?.toBigDecimal() ?: return@mapNotNull null,
                volume = candle.volume?.toBigDecimal() ?: BigDecimal.ZERO
            )
        }

    override fun getOpenOrders(pair: TradePair): List<Order> =
        restClient.getOpenOrders(market = toMarket(pair))
            .mapNotNull { mapOrder(it, pair) }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        restClient.getOpenOrders()
            .mapNotNull { extOrder ->
                val orderMarket = extOrder.market ?: return@mapNotNull null
                val orderPair = TradePair(orderMarket)
                if (pairs.contains(orderPair)) {
                    mapOrder(extOrder, orderPair)
                } else null
            }
            .groupBy { it.pair }

    override fun getBalances(): Map<String, List<Balance>> {
        val balance = restClient.getBalance()
        val asset = balance.currency ?: "USD"
        return mapOf(
            "EXTENDED" to listOf(
                Balance(
                    asset = asset,
                    total = balance.equity?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    free = balance.availableForTrade?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    locked = (balance.margin?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                )
            )
        )
    }

    override fun getBalance(coin: String): Balance {
        val balance = restClient.getBalance()
        return Balance(
            asset = balance.currency ?: "USD",
            total = balance.equity?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            free = balance.availableForTrade?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            locked = (balance.margin?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        )
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook {
        val book = restClient.getOrderBook(toMarket(pair))
        return OrderBook(
            bids = book.bids.map { Offer(it[0].toBigDecimal(), it[1].toBigDecimal()) },
            asks = book.asks.map { Offer(it[0].toBigDecimal(), it[1].toBigDecimal()) }
        )
    }

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        mapOf("EXTENDED" to getBalance(asset))

    override fun getOrder(pair: TradePair, orderId: String): Order? {
        val extOrder = restClient.getOrderById(orderId) ?: return null
        return mapOrder(extOrder, pair)
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        val request = ExtendedCreateOrderRequest(
            market = toMarket(order.pair),
            side = when (order.side) {
                SIDE.BUY -> "BUY"
                SIDE.SELL -> "SELL"
                else -> throw UnknownOrderSide("Error, side: ${order.side}")
            },
            type = when (order.type) {
                TYPE.LIMIT -> "LIMIT"
                TYPE.MARKET -> "MARKET"
                else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${order.type}'!")
            },
            quantity = qty,
            price = if (order.type == TYPE.LIMIT) price else null,
            reduceOnly = if (isReduceOnly) true else null
        )

        val resp = restClient.createOrder(request)
        return Order(
            orderId = resp.id ?: resp.externalId ?: "unknown",
            pair = order.pair,
            price = order.price,
            origQty = order.origQty,
            executedQty = BigDecimal.ZERO,
            side = order.side,
            type = order.type,
            status = STATUS.NEW
        )
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        return try {
            restClient.cancelOrder(orderId)
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Can't cancel order with ID = $orderId", t)
            false
        }
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream =
        StreamExtendedImpl(
            pair = pair,
            queue = queue,
            apiKey = apiKey
        )

    override fun close() {
        restClient.close()
    }

    override fun getPositions(pair: TradePair): List<Position> =
        restClient.getPositions(market = toMarket(pair))
            .map { pos ->
                val size = pos.size?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val entryPrice = pos.entryPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val realizedPnl = pos.realizedPnl?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val side = pos.side ?: "NONE"

                Position(
                    pair = pair,
                    marketPrice = pos.markPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    unrealisedPnl = pos.unrealizedPnl?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    realisedPnl = realizedPnl,
                    entryPrice = entryPrice,
                    breakEvenPrice = if (entryPrice == BigDecimal.ZERO || size == BigDecimal.ZERO) BigDecimal.ZERO
                    else {
                        if (side.equals("BUY", true) || side.equals("LONG", true))
                            (entryPrice * size - realizedPnl) / size
                        else if (side.equals("SELL", true) || side.equals("SHORT", true))
                            (entryPrice * size + realizedPnl) / size
                        else entryPrice
                    },
                    leverage = pos.leverage?.toBigDecimalOrNull() ?: BigDecimal.ONE,
                    liqPrice = pos.liquidationPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    size = size,
                    side = side
                )
            }

    override fun switchMode(category: String, mode: Int, pair: TradePair?, coin: String?) {
        // Extended uses per-market leverage, no hedge mode switch
        // Use updateLeverage instead
        if (pair != null) {
            restClient.updateLeverage(toMarket(pair), mode.toString())
        }
    }

    override fun toString(): String = "EXTENDED"

    private fun toMarket(pair: TradePair): String = "${pair.first}-${pair.second}"

    private fun toExtendedInterval(interval: INTERVAL): String = when (interval) {
        INTERVAL.ONE_MINUTE -> "1m"
        INTERVAL.THREE_MINUTES -> "3m"
        INTERVAL.FIVE_MINUTES -> "5m"
        INTERVAL.FIFTEEN_MINUTES -> "15m"
        INTERVAL.HALF_HOURLY -> "30m"
        INTERVAL.HOURLY -> "1h"
        INTERVAL.TWO_HOURLY -> "2h"
        INTERVAL.FOUR_HOURLY -> "4h"
        INTERVAL.SIX_HOURLY -> "6h"
        INTERVAL.EIGHT_HOURLY -> "8h"
        INTERVAL.TWELVE_HOURLY -> "12h"
        INTERVAL.DAILY -> "1d"
        INTERVAL.THREE_DAILY -> "3d"
        INTERVAL.WEEKLY -> "1w"
        INTERVAL.MONTHLY -> "1M"
    }

    private fun mapOrder(extOrder: exchange_api.extended.rest.response.ExtendedOrder, pair: TradePair): Order? {
        val orderId = extOrder.id ?: return null
        val side = extOrder.side?.uppercase() ?: return null

        return Order(
            orderId = orderId,
            pair = pair,
            price = extOrder.avgFillPrice?.toBigDecimalOrNull()
                ?: extOrder.price?.toBigDecimalOrNull(),
            origQty = extOrder.quantity?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            executedQty = extOrder.filledQuantity?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            side = SIDE.fromString(side),
            type = when (extOrder.type?.uppercase()) {
                "MARKET" -> TYPE.MARKET
                "LIMIT" -> TYPE.LIMIT
                else -> TYPE.LIMIT
            },
            status = mapOrderStatus(extOrder.status),
            fee = extOrder.fee?.toBigDecimalOrNull()
        )
    }

    private fun mapOrderStatus(status: String?): STATUS = when (status?.uppercase()) {
        "NEW", "OPEN" -> STATUS.NEW
        "PARTIALLY_FILLED" -> STATUS.PARTIALLY_FILLED
        "FILLED" -> STATUS.FILLED
        "CANCELLED", "CANCELED" -> STATUS.CANCELED
        "REJECTED" -> STATUS.REJECTED
        "UNTRIGGERED", "TRIGGERED", "EXPIRED" -> STATUS.UNSUPPORTED
        else -> STATUS.UNSUPPORTED
    }
}
