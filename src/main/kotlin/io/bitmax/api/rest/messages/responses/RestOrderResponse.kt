package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestOrderResponse(
        var time: Long,
        var coid: String,
        var symbol: String,
        var orderType: String,
        var baseAsset: String,
        var quoteAsset: String,
        var side: String,
        var orderPrice: String,
        var stopPrice: String,
        var orderQty: String,
        var filledQty: String,
        var fee: String,
        var feeAsset: String,
        var status: String
)