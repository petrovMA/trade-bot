package io.bitmax.api.websocket.messages.requests

import com.google.gson.annotations.SerializedName

/**
 * Cancel an Order with WebSocket
 */
class WebSocketCancelOrder {
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
     * the coid of the order to be canceled
     */
    @SerializedName("origCoid")
    var origCoid: String? = null

    /**
     * symbol
     */
    @SerializedName("symbol")
    var symbol: String? = null
    override fun toString(): String {
        return """WebSocketCancelOrder:
	messageType: $messageType
	time: $time
	coid: $coid
	symbol: $symbol
	origCoid: $origCoid"""
    }
}