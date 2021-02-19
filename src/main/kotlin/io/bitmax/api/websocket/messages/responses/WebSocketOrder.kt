package io.bitmax.api.websocket.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WebSocketOrder(
        val m: String? = null,
        val ac: String? = null,
        val accountId: String? = null,
        val data: OrderData
)

data class OrderData(
        val sn: Long, // sequence number
        val orderId: String,
        val s: String, // symbol
        val ot: String, // order type 'Limit'
        val p: String, // order price
        val q: String, // order quantity
        val sd: String, // order side 'Sell'
        val st: String, // order status 'Canceled'
        val ap: String, // average fill price
        val cfq: String, // cumulated filled qty
        val sp: String, // stop price; could be empty
        val err: String, // error code; could be empty
        val btb: String, // base asset total balance
        val bab: String, // base asset available balance
        val qtb: String, // quote asset total balance
        val qab: String, // quote asset available balance
        val cf: String, // cumulated commission
        val fa: String, // fee asset
        val ei: String // execution instruction 'NULL_VAL'
)