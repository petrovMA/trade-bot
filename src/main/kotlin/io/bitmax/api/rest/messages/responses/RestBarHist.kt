package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.gson.annotations.SerializedName

@JsonIgnoreProperties(ignoreUnknown = true)
class RestBarHist {
    /**
     * message
     */
    @SerializedName("m")
    var message: String? = null

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
     * interval: 1/5/30/60/360/1d
     */
    @SerializedName("i")
    var interval: String? = null

    /**
     * time
     */
    @SerializedName("t")
    var time: Long = 0

    /**
     * open
     */
    @SerializedName("o")
    var open: String? = null

    /**
     * close
     */
    @SerializedName("c")
    var close: String? = null

    /**
     * high
     */
    @SerializedName("h")
    var high: String? = null

    /**
     * low
     */
    @SerializedName("l")
    var low: String? = null

    /**
     * volume
     */
    @SerializedName("v")
    var volume: String? = null
    override fun toString(): String {
        return """
BarHist:
	message: $message
	symbol: $symbol
	baseAsset: $baseAsset
	quoteAsset: $quoteAsset
	interval: $interval
	time: $time
	open: $open
	close: $close
	high: $high
	low: $low
	volume: $volume"""
    }
}