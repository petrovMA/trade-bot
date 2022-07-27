package io.bybit.api.websocket.messages.response.instrument_info

data class InstrumentInfo(
    val cross_seq: Long,
    val `data`: Data,
    val timestamp_e6: Long,
    val topic: String,
    val type: String
)