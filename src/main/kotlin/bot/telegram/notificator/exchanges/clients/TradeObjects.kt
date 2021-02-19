package bot.telegram.notificator.exchanges.clients

interface CommonExchangeData

data class Balance(val asset: String, val total: Double, val free: Double, val locked: Double) : CommonExchangeData

data class Order(
    val orderId: String,
    val pair: TradePair,
    val price: Double,
    val origQty: Double,
    var executedQty: Double,
    val side: SIDE,
    val type: TYPE,
    var status: STATUS
) : CommonExchangeData

data class TradePair(
        val first: String,
        val second: String
) {
    constructor(pair: String): this(pair.split('_')[0], pair.split('_')[1])
    override fun toString(): String = "${first}_$second"
}

fun stub(pair: TradePair = TradePair("none", "none")): Order = Order(
        orderId = "0",
        pair = pair,
        price = 0.0,
        origQty = 0.0,
        executedQty = 0.0,
        side = SIDE.UNSUPPORTED,
        type = TYPE.UNSUPPORTED,
        status = STATUS.CANCELED
)

enum class SIDE { SELL, BUY, UNSUPPORTED }
enum class TYPE { LIMIT, MARKET, UNSUPPORTED }
enum class STATUS { PARTIALLY_FILLED, FILLED, CANCELED, NEW, REJECTED, UNSUPPORTED }
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
    MONTHLY("1M")
}

data class DepthEventOrders(val ask: OrderEntry, val bid: OrderEntry) : CommonExchangeData
data class OrderEntry(val price: Double, val qty: Double) : CommonExchangeData
data class OrderBook(val bids: List<OrderEntry>, val asks: List<OrderEntry>) : CommonExchangeData

enum class ExchangeEnum {
    BINANCE,
    BITMAX,
    STUB_TEST
}

data class Candlestick(
        val openTime: Long,
        val closeTime: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double
) : CommonExchangeData