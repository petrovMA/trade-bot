package bot.trade.exchanges.clients

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.libs.*
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.UnknownOrderStatus
import com.google.gson.annotations.SerializedName
//import info.bitrich.xchangestream.binancefuture.dto.BinanceFuturesPosition // todo: works only on org.knowm.xchange:xchange-binance:5.1.1-SNAPSHOT
import io.bybit.api.websocket.messages.response.Kline
import org.knowm.xchange.binance.dto.marketdata.BinanceKline
import org.knowm.xchange.binance.dto.trade.OrderSide
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.derivative.FuturesContract
import org.ta4j.core.Bar
import java.math.BigDecimal
import java.time.Duration

interface CommonExchangeData

data class Balance(val asset: String, val total: BigDecimal, val free: BigDecimal, val locked: BigDecimal) :
    CommonExchangeData {
    constructor(balance: org.knowm.xchange.dto.account.Balance) : this(
        asset = balance.currency.symbol,
        free = balance.available,
        locked = balance.frozen,
        total = balance.total
    )
}


data class ExchangePosition(
    val pair: String,
    val positionAmount: BigDecimal,
    val entryPrice: BigDecimal,
    val accumulatedRealized: BigDecimal,
    val unrealizedPnl: BigDecimal,
    val marginType: String,
    val isolatedWallet: BigDecimal,
    val positionSide: String?,
    val breakEvenPrice: BigDecimal
) : CommonExchangeData {
    // todo: works only on org.knowm.xchange:xchange-binance:5.1.1-SNAPSHOT
    /*constructor(position: BinanceFuturesPosition) : this(
        pair = TradePair(position.futuresContract).toString(),
        positionAmount = if (position.positionAmount == BigDecimal(0.0)) BigDecimal(0.0)
        else position.positionAmount.round(),
        entryPrice = if (position.entryPrice == BigDecimal(0.0)) BigDecimal(0.0)
        else position.entryPrice.round(),
        accumulatedRealized = if (position.accumulatedRealized == BigDecimal(0.0)) BigDecimal(0.0)
        else position.accumulatedRealized.round(),
        unrealizedPnl = if (position.unrealizedPnl == BigDecimal(0.0)) BigDecimal(0.0)
        else position.unrealizedPnl.round(),
        marginType = position.marginType,
        isolatedWallet = if (position.isolatedWallet == BigDecimal(0.0)) BigDecimal(0.0)
        else position.isolatedWallet.round(),
        positionSide = position.positionSide,
        breakEvenPrice = if (position.positionAmount == BigDecimal(0.0)) BigDecimal(0.0)
        else (position.entryPrice - position.accumulatedRealized / position.positionAmount).round()
    )*/
}

data class Order(
    val orderId: String,
    val pair: TradePair,
    val price: BigDecimal?,
    val origQty: BigDecimal,
    var executedQty: BigDecimal,
    var side: SIDE,
    var type: TYPE,
    var status: STATUS,
    var stopPrice: BigDecimal? = null,
    var lastBorderPrice: BigDecimal? = null,
    var fee: BigDecimal? = null
) : CommonExchangeData {
    override fun toString(): String = StringOrder(
        orderId = orderId,
        pair = pair.toString(),
        price = price,
        origQty = origQty,
        executedQty = executedQty,
        side = side,
        type = type,
        status = status,
        stopPrice = stopPrice,
        lastBorderPrice = lastBorderPrice,
        fee = fee
    ).let { json(it) }

    data class StringOrder(
        val orderId: String,
        val pair: String,
        val price: BigDecimal?,
        val origQty: BigDecimal,
        val executedQty: BigDecimal,
        val side: SIDE,
        val type: TYPE,
        val status: STATUS,
        val stopPrice: BigDecimal? = null,
        val lastBorderPrice: BigDecimal? = null,
        val fee: BigDecimal? = null
    )

    constructor(data: io.bybit.api.websocket.messages.response.Order.Data) : this(
        orderId = data.orderId,
        pair = data.symbol.run { TradePair(take(3), drop(3)) },
        price = if (data.avgPrice.matches(Regex("\\d+\\.?\\d*")))
            data.avgPrice.toBigDecimal()
        else
            data.price.toBigDecimal(),
        origQty = data.qty.toBigDecimal(),
        executedQty = data.cumExecQty.toBigDecimal(),
        side = SIDE.valueOf(data.side.uppercase()),
        type = TYPE.valueOf(data.orderType.uppercase()),
        status = when (data.orderStatus) {
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
        },
        fee = data.cumExecFee.toBigDecimal()
    )

    constructor(activeOrder: ActiveOrder) : this(
        price = activeOrder.price,
        pair = TradePair(activeOrder.tradePair!!),
        orderId = activeOrder.orderId.toString(),
        origQty = activeOrder.amount!!,
        executedQty = BigDecimal(0),
        side = activeOrder.orderSide!!,
        type = TYPE.MARKET,
        status = STATUS.NEW,
        stopPrice = activeOrder.stopPrice,
        lastBorderPrice = activeOrder.lastBorderPrice
    )

    override fun equals(other: Any?) = other is Order
            && orderId == other.orderId
            && pair == other.pair
            && compareBigDecimal(price, other.price)
            && compareBigDecimal(origQty, other.origQty)
            && compareBigDecimal(executedQty, other.executedQty)
            && side == other.side
            && type == other.type
            && status == other.status
            && compareBigDecimal(stopPrice, other.stopPrice)
            && compareBigDecimal(lastBorderPrice, other.lastBorderPrice)
            && compareBigDecimal(fee, other.fee)

    fun equalsWithoutId(other: Any?) = other is Order
            && pair == other.pair
            && compareBigDecimal(price, other.price)
            && compareBigDecimal(origQty, other.origQty)
            && compareBigDecimal(executedQty, other.executedQty)
            && side == other.side
            && type == other.type
            && status == other.status
            && compareBigDecimal(stopPrice, other.stopPrice)
            && compareBigDecimal(lastBorderPrice, other.lastBorderPrice)
            && compareBigDecimal(fee, other.fee)

    override fun hashCode(): Int = super.hashCode()
}

data class Position(
    val pair: TradePair,
    val marketPrice: BigDecimal,
    val unrealisedPnl: BigDecimal,
    val realisedPnl: BigDecimal,
    val entryPrice: BigDecimal,
    val breakEvenPrice: BigDecimal,
    val leverage: BigDecimal,
    val liqPrice: BigDecimal,
    var size: BigDecimal,
    val side: String
) : CommonExchangeData {
    constructor(data: io.bybit.api.websocket.messages.response.Position.Data) : this(
        pair = data.symbol.run { TradePair(take(3), drop(3)) },
        marketPrice = data.markPrice.toBigDecimal(),
        unrealisedPnl = data.unrealisedPnl.toBigDecimal(),
        realisedPnl = data.curRealisedPnl.toBigDecimal(),
        entryPrice = data.entryPrice.toBigDecimal(),

        breakEvenPrice = if (data.entryPrice.toBigDecimal() == BigDecimal(0)) BigDecimal(0)
        else {
            if (data.side.equals("BUY", true))
                (data.entryPrice.toBigDecimal() * data.size.toBigDecimal() - data.curRealisedPnl.toBigDecimal()) / data.size.toBigDecimal()
            else if (data.side.equals("SELL", true))
                (data.entryPrice.toBigDecimal() * data.size.toBigDecimal() + data.curRealisedPnl.toBigDecimal()) / data.size.toBigDecimal()
            else throw Exception("Unsupported order side = ${data.side}")
        },
        leverage = data.leverage.toBigDecimal(),
        liqPrice = data.liqPrice.let {
            if (it.isEmpty()) 0.toBigDecimal()
            else it.toBigDecimal()
        },
        size = data.size.toBigDecimal(),
        side = data.side
    )

    constructor(data: io.bybit.api.rest.response.PositionResponse.Result.Position) : this(
        pair = data.symbol.run { TradePair(take(3), drop(3)) },
        marketPrice = data.markPrice.toBigDecimal(),
        unrealisedPnl = data.unrealisedPnl.toBigDecimal(),
        realisedPnl = data.curRealisedPnl.toBigDecimal(),
        entryPrice = data.avgPrice.toBigDecimal(),

        breakEvenPrice = if (data.avgPrice.toBigDecimal() == BigDecimal(0)) BigDecimal(0)
        else {
            if (data.side.equals("BUY", true))
                (data.avgPrice.toBigDecimal() * data.size.toBigDecimal() - data.curRealisedPnl.toBigDecimal()) / data.size.toBigDecimal()
            else if (data.side.equals("SELL", true))
                (data.avgPrice.toBigDecimal() * data.size.toBigDecimal() + data.curRealisedPnl.toBigDecimal()) / data.size.toBigDecimal()
            else throw Exception("Unsupported order side = ${data.side}")
        },
        leverage = data.leverage.toBigDecimal(),
        liqPrice = data.liqPrice.let {
            if (it.isEmpty()) 0.toBigDecimal()
            else it.toBigDecimal()
        },
        size = data.size.toBigDecimal(),
        side = data.side
    )

    override fun equals(other: Any?) = other is Position
            && side == other.side
            && pair == other.pair
            && compareBigDecimal(marketPrice, other.marketPrice)
            && compareBigDecimal(unrealisedPnl, other.unrealisedPnl)
            && compareBigDecimal(realisedPnl, other.realisedPnl)
            && compareBigDecimal(entryPrice, other.entryPrice)
            && compareBigDecimal(breakEvenPrice, other.breakEvenPrice)
            && compareBigDecimal(leverage, other.leverage)
            && compareBigDecimal(liqPrice, other.liqPrice)
            && compareBigDecimal(size, other.size)

    override fun hashCode(): Int = super.hashCode()
}

data class TradePair(val first: String, val second: String) {

    constructor(pair: String) : this(
        first = pair.split("[_\\\\/\\-|\\s]".toRegex())[0],
        second = pair.split("[_\\\\/\\-|\\s]".toRegex())[1]
    )

    constructor(pair: CurrencyPair) : this(pair.base.currencyCode, pair.counter.currencyCode)

    constructor(pair: FuturesContract) : this(pair.toString())

    override fun toString(): String = "${first}_$second"
    fun toCurrencyPair() = CurrencyPair(first, second)

    override fun equals(other: Any?) = other is TradePair
            && other.first.equals(first, true)
            && other.second.equals(second, true)

    override fun hashCode(): Int = 31 * first.hashCode() + second.hashCode()
}

fun stub(pair: TradePair = TradePair("none", "none")): Order = Order(
    orderId = "0",
    pair = pair,
    price = BigDecimal(0),
    origQty = BigDecimal(0),
    executedQty = BigDecimal(0),
    side = SIDE.UNSUPPORTED,
    type = TYPE.UNSUPPORTED,
    status = STATUS.CANCELED
)

enum class SIDE {
    SELL,
    BUY,
    UNSUPPORTED;

    companion object {
        fun valueOf(type: org.knowm.xchange.dto.Order.OrderType) = when (type) {
            org.knowm.xchange.dto.Order.OrderType.BID -> BUY
            org.knowm.xchange.dto.Order.OrderType.ASK -> SELL
            else -> throw UnknownOrderSide("Error, type: $type")
        }

        fun valueOf(side: OrderSide) = when (side) {
            OrderSide.BUY -> BUY
            OrderSide.SELL -> SELL
            else -> throw UnknownOrderSide("Error, side: $side")
        }
    }

    fun toType() = when (this) {
        BUY -> org.knowm.xchange.dto.Order.OrderType.BID
        SELL -> org.knowm.xchange.dto.Order.OrderType.ASK
        else -> throw UnknownOrderSide("Error, side: $this")
    }
}

enum class TYPE { LIMIT, MARKET, UNSUPPORTED }
enum class DIRECTION { LONG, SHORT }
enum class STATUS {
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    NEW,
    REJECTED,
    UNSUPPORTED;

    companion object {
        fun valueOf(status: org.knowm.xchange.dto.Order.OrderStatus) = when (status) {
            org.knowm.xchange.dto.Order.OrderStatus.NEW -> NEW
            org.knowm.xchange.dto.Order.OrderStatus.PENDING_NEW -> NEW
            org.knowm.xchange.dto.Order.OrderStatus.OPEN -> NEW

            org.knowm.xchange.dto.Order.OrderStatus.CANCELED -> CANCELED
            org.knowm.xchange.dto.Order.OrderStatus.REJECTED -> CANCELED
            org.knowm.xchange.dto.Order.OrderStatus.EXPIRED -> CANCELED
            org.knowm.xchange.dto.Order.OrderStatus.CLOSED -> CANCELED
            org.knowm.xchange.dto.Order.OrderStatus.STOPPED -> CANCELED
            org.knowm.xchange.dto.Order.OrderStatus.REPLACED -> CANCELED

            org.knowm.xchange.dto.Order.OrderStatus.FILLED -> FILLED
            org.knowm.xchange.dto.Order.OrderStatus.PARTIALLY_FILLED -> PARTIALLY_FILLED
            else -> throw UnknownOrderStatus("Error: Unknown status '$status'!")
        }

        fun valueOf(status: org.knowm.xchange.binance.dto.trade.OrderStatus) = when (status) {
            org.knowm.xchange.binance.dto.trade.OrderStatus.NEW -> NEW

            org.knowm.xchange.binance.dto.trade.OrderStatus.CANCELED -> CANCELED
            org.knowm.xchange.binance.dto.trade.OrderStatus.REJECTED -> CANCELED
            org.knowm.xchange.binance.dto.trade.OrderStatus.EXPIRED -> CANCELED
            org.knowm.xchange.binance.dto.trade.OrderStatus.PENDING_CANCEL -> CANCELED

            org.knowm.xchange.binance.dto.trade.OrderStatus.FILLED -> FILLED
            org.knowm.xchange.binance.dto.trade.OrderStatus.PARTIALLY_FILLED -> PARTIALLY_FILLED
            else -> throw UnknownOrderStatus("Error: Unknown status '$status'!")
        }
    }
}

enum class INTERVAL(val time: String) {
    ONE_MINUTE("1m"),
    THREE_MINUTES("3m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    HALF_HOURLY("30m"),
    HOURLY("1h"),
    TWO_HOURLY("2h"),
    FOUR_HOURLY("4h"),
    SIX_HOURLY("6h"),
    EIGHT_HOURLY("8h"),
    TWELVE_HOURLY("12h"),
    DAILY("1d"),
    THREE_DAILY("3d"),
    WEEKLY("1w"),
    MONTHLY("1M");


    fun toMillsTime(): Long = when (this) {
        ONE_MINUTE -> 60_000L
        THREE_MINUTES -> 180_000L
        FIVE_MINUTES -> 300_000L
        FIFTEEN_MINUTES -> 900_000L
        HALF_HOURLY -> 1_800_000L
        HOURLY -> 3_600_000L
        TWO_HOURLY -> 3_600_000L * 2
        FOUR_HOURLY -> 3_600_000L * 4
        SIX_HOURLY -> 3_600_000L * 6
        EIGHT_HOURLY -> 3_600_000L * 8
        TWELVE_HOURLY -> 3_600_000L * 12
        DAILY -> 3_600_000L * 24
        THREE_DAILY -> 3_600_000L * 24 * 3
        WEEKLY -> 3_600_000L * 24 * 7
        MONTHLY -> 3_600_000L * 24 * 31
    }

    fun toDuration() = toMillsTime().ms()
}

data class DepthEventOrders(val ask: Offer, val bid: Offer) : CommonExchangeData
data class Trade(val price: BigDecimal, val qty: BigDecimal, val time: Long) : CommonExchangeData
data class Offer(val price: BigDecimal, val qty: BigDecimal) : CommonExchangeData
data class OrderBook(val bids: List<Offer>, val asks: List<Offer>) : CommonExchangeData

enum class ExchangeEnum {
    BYBIT,
    BINANCE,
    BINANCE_FUTURES,
    BITMAX,
    HUOBI,
    GATE,
    STUB_TEST,
    TEST
}

data class Candlestick(
    val openTime: Long,
    val closeTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal
) : CommonExchangeData {
    constructor(kline: BinanceKline) : this(
        openTime = kline.openTime,
        closeTime = kline.closeTime,
        open = kline.open,
        high = kline.high,
        low = kline.low,
        close = kline.close,
        volume = kline.volume
    )

    constructor(kline: Kline.Data) : this(
        openTime = kline.start,
        closeTime = kline.end,
        open = kline.open.toBigDecimal(),
        high = kline.high.toBigDecimal(),
        low = kline.low.toBigDecimal(),
        close = kline.close.toBigDecimal(),
        volume = kline.volume.toBigDecimal()
    )

    constructor(bar: Bar) : this(
        openTime = bar.beginTime.toInstant().toEpochMilli(),
        closeTime = bar.endTime.toInstant().toEpochMilli(),
        open = bar.openPrice.doubleValue().toBigDecimal(),
        high = bar.highPrice.doubleValue().toBigDecimal(),
        low = bar.lowPrice.doubleValue().toBigDecimal(),
        close = bar.closePrice.doubleValue().toBigDecimal(),
        volume = bar.volume.doubleValue().toBigDecimal()
    )

    constructor(line: List<String>, interval: Duration) : this(
        openTime = line[0].toLong(),
        closeTime = line[0].toLong() + interval.toMillis(),
        open = line[1].toBigDecimal(),
        high = line[2].toBigDecimal(),
        low = line[3].toBigDecimal(),
        close = line[4].toBigDecimal(),
        volume = line[5].toBigDecimal()
    )

    override fun equals(other: Any?): Boolean = other is Candlestick
            && other.openTime == openTime
            && other.closeTime == closeTime
            && compareBigDecimal(other.open, open)
            && compareBigDecimal(other.high, high)
            && compareBigDecimal(other.low, low)
            && compareBigDecimal(other.close, close)
            && compareBigDecimal(other.volume, volume)
}

class BotEmulateParams(
    @SerializedName("from") val from: String?,
    @SerializedName("to") val to: String?,
    @SerializedName("fee") val fee: BigDecimal?,
    @SerializedName("fail_if_kline_gaps") val failIfKlineGaps: Boolean?,
    @SerializedName("bot_params") val botParams: BotSettingsTrader,
    @SerializedName("is_write_orders_to_log") val isWriteOrdersToLog: Boolean?
)

abstract class BotSettings(
    val type: String = "",
    open val name: String,
    open val pair: TradePair,
    open val exchange: String,
    val orderBalanceType: String,
    val countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    val countOfDigitsAfterDotForPrice: Int // number of characters after the dot for price
)

class BotSettingsTrader(
    @SerializedName("flow_name") override val name: String,
    @SerializedName("symbol") override val pair: TradePair,
    @SerializedName("exchange_type") override val exchange: String,
    orderBalanceType: String = "first", // if first => BTC balance, else second => USDT balance (default = second)
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    @SerializedName("strategy_type") val strategy: StrategyType,
    @SerializedName("order_type") val ordersType: TYPE,
    @SerializedName("parameters") val parameters: TradeParameters,
    @SerializedName("trend_detector") val trendDetector: TrendDetector? = null,
    @SerializedName("min_order_amount") val minOrderAmount: MinOrderAmount? = null,
    @SerializedName("market_type") val marketType: String,
    @SerializedName("market_type_comment") val marketTypeComment: String,
    @SerializedName("strategy_type_comment") val strategyTypeComment: String,
    @SerializedName("auto_balance") val autoBalance: Boolean = false
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange.uppercase(),
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
) {

    class MinOrderAmount(
        @SerializedName("amount") val amount: BigDecimal,
        @SerializedName("countOfDigitsAfterDotForAmount") val countOfDigitsAfterDotForAmount: Int = 0
    )

    enum class StrategyType { LONG, SHORT, BOTH }

    class TrendDetector(
        @SerializedName("not_auto_calc_trend") val notAutoCalcTrend: Boolean = false,
        @SerializedName("rsi1") val rsi1: Rsi,
        @SerializedName("rsi2") val rsi2: Rsi,
        @SerializedName("hma_parameters") val hmaParameters: HmaParameters,
        @SerializedName("input_kline_interval") val inputKlineInterval: String?
    ) {
        class Rsi(
            @SerializedName("rsi_period") val rsiPeriod: Int,
            @SerializedName("time_frame") val timeFrame: String
        )

        class HmaParameters(
            @SerializedName("hma1_period") val hma1Period: Int,
            @SerializedName("hma2_period") val hma2Period: Int,
            @SerializedName("hma3_period") val hma3Period: Int,
            @SerializedName("time_frame") val timeFrame: String
        )
    }

    class TradeParameters(
        @SerializedName("long_parameters") val longParameters: Parameters?,
        @SerializedName("short_parameters") val shortParameters: Parameters?,
    ) {
        class Parameters(
            @SerializedName("trading_range") val tradingRange: TradingRange, // Trading Range:: range of price for orders
            @SerializedName("in_order_quantity") val inOrderQuantity: InOrderQuantity, // Order Quantity:: order size
            @SerializedName("in_order_distance") val inOrderDistance: InOrderDistance, // Order Distance:: distance between every order
            @SerializedName("trailing_in_order_distance") val trailingInOrderDistance: TrailingInOrderDistance?, // Trailing in Distance:: distance between in_order and when_order_be_executed
            @SerializedName("trigger_in_order_distance") val triggerInOrderDistance: TriggerInOrderDistance?, // Trigger in Distance
            @SerializedName("trigger_distance") val triggerDistance: TriggerDistance, // Trigger Distance:: distance between order and stop-order
            @SerializedName("min_tp_distance") val minTpDistance: MinTpDistance,
            @SerializedName("max_tp_distance") val maxTpDistance: MaxTpDistance,
            @SerializedName("max_trigger_count") val orderMaxQuantity: Int, // Max Order count:: max amount of orders
            @SerializedName("set_close_orders") val setCloseOrders: Boolean = true, // set close position orders when bot starts
            @SerializedName("counter_distance") val counterDistance: BigDecimal? = null,
            @SerializedName("use_realized_pnl_in_calc_profit") val withRealizedPnl: Boolean? = null,
            @SerializedName("entire_tp") val entireTp: EntireTp?
        ) {
            class TradingRange(
                @SerializedName("lower_bound") val lowerBound: BigDecimal,
                @SerializedName("upper_bound") val upperBound: BigDecimal
            )

            class InOrderQuantity(
                @SerializedName("value") val value: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class InOrderDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class TrailingInOrderDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class TriggerInOrderDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class TriggerDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class MaxTpDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class MinTpDistance(
                @SerializedName("distance") val distance: BigDecimal,
                @SerializedName("use_percent") val usePercent: Boolean = false
            )

            class EntireTp(
                @SerializedName("max_trigger_amount") val maxTriggerAmount: Int,
                @SerializedName("max_profit_percent") val maxProfitPercent: BigDecimal,
                @SerializedName("max_loss_percent") val maxLossPercent: BigDecimal,
                @SerializedName("enabled_in_hedge") val enabledInHedge: Boolean,
                @SerializedName("enabled") val enabled: Boolean,
                @SerializedName("tp_distance") val tpDistance: TpDistance
            ) {
                class TpDistance(
                    @SerializedName("distance") val distance: BigDecimal,
                    @SerializedName("use_percent") val usePercent: Boolean = false
                )
            }


            fun minRange() = tradingRange.lowerBound
            fun maxRange() = tradingRange.upperBound
            fun orderDistance() = inOrderDistance
            fun orderQuantity() = inOrderQuantity.value
            fun triggerDistance() = triggerDistance.distance
            fun orderMaxQuantity() = orderMaxQuantity
            fun triggerInOrderDistance() = triggerInOrderDistance?.distance
            fun minTpDistance() = minTpDistance.distance
            fun maxTpDistance() = maxTpDistance.distance
            fun trailingInOrderDistance() = trailingInOrderDistance?.distance
            fun setCloseOrders() = setCloseOrders
        }
    }
}

class BotSettingsBobblesIndicator(
    name: String,
    pair: TradePair,
    exchange: String,
    orderBalanceType: String,
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    val feePercent: BigDecimal = BigDecimal(0.1), // fee for calc profit
    val minOrderSize: BigDecimal, // min order size
    val buyAmountMultiplication: BigDecimal, // buy order size Multiplication
    val sellAmountMultiplication: BigDecimal, // sell order size Multiplication
    val maxShortPosition: BigDecimal = BigDecimal(0.0) // max available short position
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange,
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
)

class BotSettingsGrid(
    @SerializedName("flow_name") override val name: String,
    @SerializedName("symbol") override val pair: TradePair,
    @SerializedName("exchange_type") override val exchange: String,
    orderBalanceType: String = "first", // if first => BTC balance, else second => USDT balance (default = second)
    countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    @SerializedName("parameters") val parameters: Parameters,
    @SerializedName("trend_detector") val trendDetector: BotSettingsTrader.TrendDetector? = null,
    @SerializedName("min_order_amount") val minOrderAmount: BotSettingsTrader.MinOrderAmount? = null,
    @SerializedName("market_type") val marketType: String,
    @SerializedName("market_type_comment") val marketTypeComment: String,
    @SerializedName("strategy_type_comment") val strategyTypeComment: String,
    @SerializedName("auto_balance") val autoBalance: Boolean = false
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange.uppercase(),
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice
) {

    class Parameters(
        @SerializedName("trading_range") val tradingRange: TradingRange, // Trading Range:: range of price for orders
    ) {
        class TradingRange(
            @SerializedName("lower_bound") val lowerBound: BigDecimal,
            @SerializedName("upper_bound") val upperBound: BigDecimal
        )
    }
}
