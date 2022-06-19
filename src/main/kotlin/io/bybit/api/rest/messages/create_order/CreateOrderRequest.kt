package io.bybit.api.rest.messages.create_order

data class CreateOrderRequest(
    val api_key: String,
    val order_type: String,
    val qty: String,
    val side: String,
    val sign: String,
    val symbol: String,
    val time_in_force: String,
    val timestamp: String
)