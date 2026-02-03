package exchange_api.gate.rest.response

import java.math.BigDecimal

data class GateOrderResponse(
    val orderId: String,
    val symbol: String,
    val side: String,
    val orderType: String,
    val qty: BigDecimal,
    val price: BigDecimal,
    val status: String,
    val timeInForce: String? = null,
    val executedQty: BigDecimal = BigDecimal.ZERO,
    val cummulativeQuoteQty: BigDecimal = BigDecimal.ZERO,
    val updateTime: Long = System.currentTimeMillis()
)