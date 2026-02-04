package exchange_api.extended.rest.response

import com.google.gson.annotations.SerializedName

// Base response wrapper - Extended API returns data directly or in a wrapper
data class ExtendedApiResponse<T>(
    val data: T?,
    val error: String?,
    val message: String?
)

// GET /markets
data class ExtendedMarket(
    val market: String,           // e.g. "BTC-USD"
    val status: String,           // e.g. "ACTIVE"
    val baseAsset: String,        // e.g. "BTC"
    val quoteAsset: String,       // e.g. "USD"
    val tickSize: String,         // price precision
    val stepSize: String,         // qty precision
    val minOrderSize: String?,
    val maxOrderSize: String?,
    val maxLeverage: String?
)

// GET /orderbook
data class ExtendedOrderBookResponse(
    val market: String,
    val bids: List<List<String>>,  // [[price, qty], ...]
    val asks: List<List<String>>,
    val timestamp: Long?
)

// GET /trades
data class ExtendedTrade(
    @SerializedName("i") val id: String?,        // trade id
    @SerializedName("m") val market: String?,     // market
    @SerializedName("S") val side: String?,       // BUY/SELL
    @SerializedName("T") val timestamp: Long?,    // timestamp
    @SerializedName("p") val price: String?,      // price
    @SerializedName("q") val quantity: String?    // quantity
)

// GET /candles
data class ExtendedCandle(
    val timestamp: Long?,       // open time
    val open: String?,
    val high: String?,
    val low: String?,
    val close: String?,
    val volume: String?
)

// GET /balance
data class ExtendedBalance(
    val balance: String?,
    val equity: String?,
    val availableForTrade: String?,
    val margin: String?,
    val unrealizedPnl: String?,
    val currency: String?
)

// GET /positions
data class ExtendedPosition(
    val market: String?,
    val side: String?,             // LONG/SHORT
    val size: String?,
    val entryPrice: String?,
    val markPrice: String?,
    val liquidationPrice: String?,
    val unrealizedPnl: String?,
    val realizedPnl: String?,
    val leverage: String?,
    val marginType: String?
)

// GET /orders (single order)
data class ExtendedOrder(
    val id: String?,
    val externalId: String?,
    val market: String?,
    val side: String?,             // BUY/SELL
    val type: String?,             // LIMIT/MARKET
    val status: String?,           // NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, etc.
    val price: String?,
    val quantity: String?,
    val filledQuantity: String?,
    val avgFillPrice: String?,
    val fee: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)

// POST /orders - create order request
data class ExtendedCreateOrderRequest(
    val market: String,
    val side: String,              // BUY/SELL
    val type: String,              // LIMIT/MARKET
    val quantity: String,
    val price: String?,            // required for LIMIT
    val externalId: String? = null,
    val reduceOnly: Boolean? = null,
    val postOnly: Boolean? = null
)

// POST /orders - create order response
data class ExtendedCreateOrderResponse(
    val id: String?,
    val externalId: String?
)

// GET /leverage
data class ExtendedLeverage(
    val market: String?,
    val leverage: String?
)

// PUT /leverage
data class ExtendedUpdateLeverageRequest(
    val market: String,
    val leverage: String
)
