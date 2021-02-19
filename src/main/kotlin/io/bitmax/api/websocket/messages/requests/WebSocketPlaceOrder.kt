package io.bitmax.api.websocket.messages.requests

import com.google.gson.annotations.SerializedName

/**
 * Place a New Order with WebSocket
 */
class WebSocketPlaceOrder {
    /**
     * message type
     */
    @SerializedName("messageType")
    var messageType: String? = null

    /**
     * milliseconds since UNIX epoch in UTC
     */
    @SerializedName("time")
    var time: Long = 0

    /**
     * a 32-character unique client order Id
     */
    @SerializedName("coid")
    var coid: String? = null

    /**
     * symbol
     */
    @SerializedName("symbol")
    var symbol: String? = null

    /**
     * order price
     */
    @SerializedName("orderPrice")
    var orderPrice: String? = null

    /**
     * order quantity
     */
    @SerializedName("orderQty")
    var orderQty: String? = null

    /**
     * order type, you shall specify one of the following: "limit", "market", "stop_market", "stop_limit".
     */
    @SerializedName("orderType")
    var orderType: String? = null

    /**
     * order side "buy" or "sell"
     */
    @SerializedName("side")
    var side: String? = null

    /**
     * Optional, if true, the order will either be posted to the limit order book or be cancelled, i.e. the order cannot take liquidity; default value is false
     */
    @SerializedName("postOnly")
    var isPostOnly = false

    /**
     * optional, stop price of the order. This field is required for stop market orders and stop limit orders.
     */
    @SerializedName("stopPrice")
    var stopPrice = ""

    /**
     * Optional, default is "GTC". Currently, we support "GTC" (good-till-canceled) and "IOC" (immediate-or-cancel).
     */
    @SerializedName("timeInForce")
    var timeInForce = "GTC"
    override fun toString(): String {
        return """WebSocketPlaceOrder:
	messageType: $messageType
	time: $time
	coid: $coid
	symbol: $symbol
	orderPrice: $orderPrice
	orderQty: $orderQty
	orderType: $orderType
	side: $side
	postOnly: ${isPostOnly}
	stopPrice: $stopPrice
	timeInForce: $timeInForce"""
    }
}