package io.bybit.api.websocket.messages.response.insurance

data class Insurance(
    val `data`: List<Data>,
    val topic: String
)