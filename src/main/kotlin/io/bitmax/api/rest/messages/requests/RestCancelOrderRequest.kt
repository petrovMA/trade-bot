package io.bitmax.api.rest.messages.requests

/**
 * Cancel an Order with Rest
 */
class RestCancelOrderRequest(
    /** milliseconds since UNIX epoch in UTC */
    val time: Long = 0,

    /** a 32-character unique client order Id */
    val id: String,

    /** a 32-character unique client cancel order Id */
    val orderId: String? = null,

    /** symbol */
    val symbol: String
)