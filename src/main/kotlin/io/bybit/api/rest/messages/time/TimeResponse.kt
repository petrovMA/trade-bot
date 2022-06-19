package io.bybit.api.rest.messages.time

data class TimeResponse(
    val ext_code: String,
    val ext_info: String,
    val result: Map<Any, Any>,
    val ret_code: Int,
    val ret_msg: String,
    val time_now: String
)