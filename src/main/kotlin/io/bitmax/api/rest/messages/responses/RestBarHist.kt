package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.gson.annotations.SerializedName

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestBarHists(
    val code: Int = 0,
    val data: List<RestBarHist> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestBarHist(
    @SerializedName("m")
    var message: String? = null,

    @SerializedName("s")
    var symbol: String? = null,

    var data: RestBarHistData? = null
) {
    override fun toString(): String {
        return """
BarHist:
	message: $message
	symbol: $symbol
	data: $data"""
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestBarHistData(
    @SerializedName("i")
    var interval: String? = null,

    @SerializedName("ts")
    var time: Long = 0,

    @SerializedName("o")
    var open: String? = null,

    @SerializedName("c")
    var close: String? = null,

    @SerializedName("h")
    var high: String? = null,

    @SerializedName("l")
    var low: String? = null,

    @SerializedName("v")
    var volume: String? = null

) {
    override fun toString(): String {
        return """
RestBarHistData:
	interval: $interval
	time: $time
	open: $open
	close: $close
	high: $high
	low: $low
	volume: $volume"""
    }
}