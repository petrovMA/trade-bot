package io.bitmax.api.websocket.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.gson.annotations.SerializedName

@JsonIgnoreProperties(ignoreUnknown = true)
class WebSocketSummary {
    /**
     * symbol
     */
    @SerializedName("s")
    var symbol: String? = null

    /**
     * base asset
     */
    @SerializedName("ba")
    var baseAsset: String? = null

    /**
     * quote asset
     */
    @SerializedName("qa")
    var quoteAsset: String? = null

    /**
     * for market summary data, the interval is always 1d
     */
    @SerializedName("i")
    var interval: String? = null

    /**
     * timestamp in UTC
     */
    @SerializedName("t")
    var timestamp: Long = 0

    /**
     * open
     */
    @SerializedName("o")
    var open = 0.0

    /**
     * close
     */
    @SerializedName("c")
    var close = 0.0

    /**
     * high
     */
    @SerializedName("h")
    var high = 0.0

    /**
     * low
     */
    @SerializedName("l")
    var low = 0.0

    /**
     * volume
     */
    @SerializedName("v")
    var volume = 0.0
    override fun toString(): String {
        return """Summary:
	symbol: $symbol
	baseAsset: $baseAsset
	quoteAsset: $quoteAsset
	interval: $interval
	timestamp: $timestamp
	open: $open
	close: $close
	high: $high
	low: $low
	volume: $volume"""
    }
}