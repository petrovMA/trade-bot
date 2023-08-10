package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.libs.*
import bot.telegram.notificator.libs.UnknownOrderSide
import bot.telegram.notificator.libs.UnknownOrderStatus
import com.google.gson.annotations.SerializedName
import org.knowm.xchange.binance.dto.trade.OrderSide
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.derivative.FuturesContract
import java.math.BigDecimal

interface CommonExchangeData

data class Balance(val asset: String, val total: BigDecimal, val free: BigDecimal, val locked: BigDecimal) :
    CommonExchangeData

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

    override fun equals(other: Any?) =
        other is TradePair && other.first.equals(first, true) && other.second.equals(second, true)

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
}

data class DepthEventOrders(val ask: Offer, val bid: Offer) : CommonExchangeData
data class Trade(val price: BigDecimal, val qty: BigDecimal, val time: Long) : CommonExchangeData
data class Offer(val price: BigDecimal, val qty: BigDecimal) : CommonExchangeData
data class OrderBook(val bids: List<Offer>, val asks: List<Offer>) : CommonExchangeData

enum class ExchangeEnum {
    BINANCE,
    BINANCE_FUTURES,
    BITMAX,
    HUOBI,
    GATE,
    STUB_TEST
}

data class Candlestick(
    val openTime: Long,
    val closeTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal
) : CommonExchangeData

abstract class BotSettings(
    val type: String = "",
    @SerializedName("settings_name") open val name: String,
    @SerializedName("settings_pair") open val pair: TradePair,
    @SerializedName("settings_exchange") open val exchange: String,
    @SerializedName("settings_orderBalanceType") open val orderBalanceType: String,
    @SerializedName("settings_countOfDigitsAfterDotForAmount") open val countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    @SerializedName("settings_countOfDigitsAfterDotForPrice") open val countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    @SerializedName("settings_feePercent") open val feePercent: BigDecimal // fee for calc profit
)

data class BotSettingsTrader(
    override val name: String,
    override val pair: TradePair,
    override val exchange: String,
    val direction: DIRECTION,
    val ordersType: TYPE,
    val tradingRange: Pair<BigDecimal, BigDecimal>,
    val orderSize: BigDecimal, // Order Quantity:: order size
    override val orderBalanceType: String = "first", // if first => BTC balance, else second => USDT balance (default = second)
    val orderDistance: BigDecimal, // Order Distance:: distance between every order
    val triggerDistance: BigDecimal, // Trigger Distance:: distance between order and stop-order
    val enableStopOrderDistance: BigDecimal = BigDecimal(0), // enable stop order distance (stopOrderDistance = triggerDistance + enableStopOrderDistance)
    val orderMaxQuantity: Int, // Max Order count:: max amount of orders
    override val countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    override val countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    val setCloseOrders: Boolean = true, // set close position orders when bot starts
    override val feePercent: BigDecimal = BigDecimal(0.1) // fee for calc profit
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange,
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice,
    feePercent = feePercent
)

data class BotSettingsBobblesIndicator(
    override val name: String,
    override val pair: TradePair,
    override val exchange: String,
    override val orderBalanceType: String,
    override val countOfDigitsAfterDotForAmount: Int, // number of characters after the dot for amount
    override val countOfDigitsAfterDotForPrice: Int, // number of characters after the dot for price
    override val feePercent: BigDecimal, // fee for calc profit
    val minOrderSize: BigDecimal, // min order size
    val buyAmountMultiplication: BigDecimal, // buy order size Multiplication
    val sellAmountMultiplication: BigDecimal // sell order size Multiplication
) : BotSettings(
    name = name,
    pair = pair,
    exchange = exchange,
    orderBalanceType = orderBalanceType,
    countOfDigitsAfterDotForAmount = countOfDigitsAfterDotForAmount,
    countOfDigitsAfterDotForPrice = countOfDigitsAfterDotForPrice,
    feePercent = feePercent
)
