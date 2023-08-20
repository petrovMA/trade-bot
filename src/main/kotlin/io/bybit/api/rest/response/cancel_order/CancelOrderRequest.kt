package io.bybit.api.rest.response.cancel_order

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CancelOrderRequest(
    val api_key: String,
    val order_id: String?,
    val order_link_id: String?,
    val sign: String,
    val symbol: String,
    val timestamp: String
)