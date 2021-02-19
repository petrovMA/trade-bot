package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaceOrCancelOrder(
        val code: Int = 0,
        val message: String? = null,
        val data: PlaceOrderData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaceOrderData(
        val accountId: String,
        val ac: String,
        val action: String,
        val status: String,
        val info: PlaceOrderInfo
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaceOrderInfo(
        val symbol: String,
        val orderType: String,
        val timestamp: Long,
        val id: String,
        val orderId: String
)