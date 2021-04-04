package io.bitmax.api.websocket.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WebSocketMarketTrades(
        var m: String,
        var symbol: String? = null,
        var data: List<TradesData>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TradesData(
        /** price */
        var p: String,

        /** quantity */
        val q: String,

        /** timestamp */
        val ts: Long = 0
)