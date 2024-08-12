package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.StreamByBitFuturesImpl
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.UnsupportedOrderTypeException
import io.bybit.api.rest.client.ByBitRestApiClient
import io.bybit.api.rest.response.OpenOrders
import mu.KotlinLogging
import org.knowm.xchange.binance.dto.marketdata.KlineInterval
import org.knowm.xchange.binance.dto.trade.OrderSide
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue


class ClientByBit(private val api: String? = null, private val sec: String? = null) : ClientFutures {

    val client = if (api != null && sec != null) ByBitRestApiClient(api, sec)
    else throw IllegalArgumentException("api and sec must be not null")

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> =
        client.getInstrumentsInfo().list.map { TradePair(it.baseCoin, it.quoteCoin) }

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
            category = "spot",
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
                category = "spot", // linear -> for perpetual contracts
                symbol = pair.first + pair.second,
                cursor = cursor,
                limit = 50
            )

            list = response.list
            cursor = response.nextPageCursor
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
                    status = when (it.orderStatus) {
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
                )
            }
    }


    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        client.getOpenOrders() // linear -> for perpetual contracts
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
                    status = when (it.orderStatus) {
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


    override fun getOrder(pair: TradePair, orderId: String): Order? = client.getOpenOrders(orderId = orderId)
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
                status = when (orderStatus) {
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
            )
        } ?: client.getOrdersHistory(orderId = orderId)
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
                status = when (orderStatus) {
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
            category = "spot", // linear -> for perpetual contracts
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
            positionIdx = when (positionSide) {
                DIRECTION.LONG -> 1
                DIRECTION.SHORT -> 2
                else -> 0
            },
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
                category = "spot"
            )
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Can't cancel order with ID = $orderId", t)
            return false
        }
    }

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


    override fun stream(pairs: List<TradePair>, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamByBitFuturesImpl(
            pairsQueues = pairs.associateWith { queue },
            sec = sec,
            api = api
        )

    override fun close() {}

    override fun switchMode(category: String, mode: Int, pair: TradePair?, coin: String?) =
        client.switchMode(category, mode, pair?.run { first + second }, coin)

    override fun getPositions(pair: TradePair) = client.getPositionsList(symbol = pair.first + pair.second)
        .list
        .map { Position(it) }


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

    private fun asCandlestick(candlestick: List<String>, interval: INTERVAL): Candlestick = Candlestick(
        openTime = candlestick[0].toLong(),
        closeTime = candlestick[0].toLong() + interval.toMillsTime(),
        open = candlestick[1].toBigDecimal(),
        high = candlestick[2].toBigDecimal(),
        low = candlestick[3].toBigDecimal(),
        close = candlestick[4].toBigDecimal(),
        volume = candlestick[5].toBigDecimal()
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

    private fun INTERVAL.toByBitInterval(): ByBitRestApiClient.INTERVAL = when (this) {
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
}