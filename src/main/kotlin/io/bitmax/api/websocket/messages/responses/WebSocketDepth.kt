package io.bitmax.api.websocket.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WebSocketDepth(
        val m: String,
        val symbol: String? = null,
        val data: DepthData
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DepthData(
        val ts: Long = 0,
        val seqnum: Long = 0,
        val asks: List<List<String>>,
        val bids: List<List<String>>
)