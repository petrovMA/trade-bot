package io.bybit.api.rest.messages.cancel_order

data class Result(
    val created_at: String,
    val cum_exec_fee: Int,
    val cum_exec_qty: Int,
    val cum_exec_value: Int,
    val last_exec_price: Int,
    val last_exec_time: Int,
    val leaves_qty: Int,
    val order_id: String,
    val order_link_id: String,
    val order_status: String,
    val order_type: String,
    val price: Int,
    val qty: Int,
    val reject_reason: String,
    val side: String,
    val symbol: String,
    val time_in_force: String,
    val updated_at: String,
    val user_id: Int
)