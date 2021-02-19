package io.bitmax.api.rest.messages.requests

/**
 * Place a New Order with Rest
 */
data class RestPlaceOrderRequest(

        /** a 32-character unique client order Id */
        val id: String,
        val time: Long = 0,
        val symbol: String,

        /** order price */
        val orderPrice: String,

        /** order quantity */
        val orderQty: String,

        /** order type, you shall specify one of the following: "limit", "market", "stop_market", "stop_limit". */
        val orderType: String,

        /** order side "buy" or "sell" */
        val side: String? = null,

        /** Optional, if true, the order will either be posted to the limit order book or be cancelled, i.e. the order cannot take liquidity; default value is false */
        val postOnly: Boolean = false,

        /** optional, stop price of the order. This field is required for stop market orders and stop limit orders. */
        val stopPrice: String? = null
)