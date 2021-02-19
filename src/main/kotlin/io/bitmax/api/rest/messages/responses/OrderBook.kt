package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderBook(
        val code: Int = 0,
        val data: OrderBookData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderBookData(
        val m: String,
        val symbol: String,
        val data: AsksBidsData,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AsksBidsData(
        val seqnum: Long,
        val ts: Long,
        val asks: List<List<String>>,
        val bids: List<List<String>>,
)