package io.bybit.api.websocket.messages.response

data class InstrumentInfo(
    val cross_seq: Long,
    val `data`: Data,
    val timestamp_e6: Long,
    val topic: String,
    val type: String
) {
    data class Data(
        val ask1_price: String,
        val ask1_price_e4: Long,
        val bid1_price: String,
        val bid1_price_e4: Long,
        val countdown_hour: Long,
        val created_at: String,
        val cross_seq: Long,
        val funding_rate_e6: Long,
        val funding_rate_interval: Long,
        val high_price_24h: String,
        val high_price_24h_e4: Long,
        val id: Long,
        val index_price: String,
        val index_price_e4: Long,
        val last_price: String,
        val last_price_e4: Long,
        val last_tick_direction: String,
        val low_price_24h: String,
        val low_price_24h_e4: Long,
        val mark_price: String,
        val mark_price_e4: Long,
        val next_funding_time: String,
        val open_interest: Long,
        val open_value_e8: Long,
        val predicted_funding_rate_e6: Long,
        val prev_price_1h: String,
        val prev_price_1h_e4: Long,
        val prev_price_24h: String,
        val prev_price_24h_e4: Long,
        val price_1h_pcnt_e6: Long,
        val price_24h_pcnt_e6: Long,
        val symbol: String,
        val total_turnover_e8: Long,
        val total_volume: Long,
        val turnover_24h_e8: Long,
        val updated_at: String,
        val volume_24h: Long
    )
}