package io.bybit.api.websocket.messages.response.liquidation

data class Liquidation(
    val `data`: Data,
    val topic: String
)