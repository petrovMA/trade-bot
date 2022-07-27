package io.bybit.api.websocket.messages.response.insurance

data class Data(
    val currency: String,
    val timestamp: String,
    val wallet_balance: Long
)