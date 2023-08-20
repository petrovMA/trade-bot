package io.bybit.api.rest.response.order_list

data class Data(
    val created_at: String,
    val cum_exec_fee: String,
    val cum_exec_qty: String,
    val cum_exec_value: String,
    val leaves_qty: String,
    val leaves_value: String,
    val order_id: String,
    val order_link_id: String,
    val order_status: String,
    val order_type: String,
    val price: String,
    val qty: String,
    val reject_reason: String,
    val side: String,
    val symbol: String,
    val time_in_force: String,
    val updated_at: String,
    val user_id: Int
)