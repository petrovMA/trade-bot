package io.bybit.api.rest.messages.kline

data class Result(
    val close: String,
    val high: String,
    val interval: String,
    val low: String,
    val `open`: String,
    val open_time: Int,
    val symbol: String,
    val turnover: String,
    val volume: String
)