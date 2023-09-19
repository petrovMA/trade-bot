package io.bybit.api.websocket.messages.response

import com.google.gson.annotations.SerializedName

data class Trade(
    val `data`: List<Data>,
    val topic: String
) {
    data class Data(
        val cross_seq: Long,
        @SerializedName("p") val price: Double,
        @SerializedName("S") val side: String,
        @SerializedName("v") val volume: String,
        @SerializedName("s") val symbol: String,
        @SerializedName("L") val direction: String,
        @SerializedName("T") val timestamp: Long,
        @SerializedName("i") val trade_id: String
    )
}