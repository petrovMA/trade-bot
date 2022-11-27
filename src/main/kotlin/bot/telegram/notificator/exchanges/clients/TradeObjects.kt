package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.libs.UnknownOrderSide
import bot.telegram.notificator.libs.UnknownOrderStatus
import org.knowm.xchange.currency.CurrencyPair
import java.math.BigDecimal

interface CommonExchangeData

data class Balance(val asset: String, val total: BigDecimal, val free: BigDecimal, val locked: BigDecimal) :
    CommonExchangeData

data class Order(
    val orderId: String,
    val pair: TradePair,
    val price: BigDecimal,
    val origQty: BigDecimal,
    var executedQty: BigDecimal,
    val side: SIDE,
    var type: TYPE,
    var status: STATUS,
    var stopPrice: BigDecimal? = null,
    var lastBorderPrice: BigDecimal? = null
) : CommonExchangeData

data class TradePair(val first: String, val second: String) {
    constructor(pair: String) : this(pair.split('_')[0], pair.split('_')[1])
    constructor(pair: CurrencyPair) : this(pair.base.currencyCode, pair.counter.currencyCode)

    override fun toString(): String = "${first}_$second"
    fun toCurrencyPair() = CurrencyPair(first, second)
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

data class BotSettings(
    val name: String,
    val pair: String,
    val direction: DIRECTION,
    val ordersType: TYPE,
    val tradingRange: Pair<BigDecimal, BigDecimal>,
    val orderSize: BigDecimal, // Order Quantity:: order size
    val orderDistance: BigDecimal, // Order Distance:: distance between every order
    val triggerDistance: BigDecimal, // Trigger Distance:: distance between order and stop-order
    val orderMaxQuantity: Int, // Max Trigger count:: max amount of orders
    val firstBalance: BigDecimal, // for example, pair BTC_USDT => BTC balance
    val secondBalance: BigDecimal // for example, pair BTC_USDT => USDT balance
)
