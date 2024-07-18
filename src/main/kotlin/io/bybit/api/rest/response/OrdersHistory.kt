package io.bybit.api.rest.response

class OrdersHistory(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Result
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time) {
    data class Result(
        val list: List<ByBitOrder>,
        val nextPageCursor: String,
        val category: String
    ) {
        data class ByBitOrder(
            val avgPrice: String,
            val blockTradeId: String,
            val cancelType: String,
            val closeOnTrigger: Boolean,
            val createdTime: String,
            val cumExecFee: String,
            val cumExecQty: String,
            val cumExecValue: String,
            val isLeverage: String,
            val lastPriceOnCreated: String,
            val leavesQty: String,
            val leavesValue: String,
            val orderId: String,
            val orderIv: String,
            val orderLinkId: String,
            val orderStatus: String,
            val orderType: String,
            val placeType: String,
            val positionIdx: Int,
            val price: String,
            val qty: String,
            val reduceOnly: Boolean,
            val rejectReason: String,
            val side: String,
            val slLimitPrice: String,
            val slTriggerBy: String,
            val smpGroup: Int,
            val smpOrderId: String,
            val smpType: String,
            val stopLoss: String,
            val stopOrderType: String,
            val symbol: String,
            val takeProfit: String,
            val timeInForce: String,
            val tpLimitPrice: String,
            val tpTriggerBy: String,
            val tpslMode: String,
            val triggerBy: String,
            val triggerDirection: Int,
            val triggerPrice: String,
            val updatedTime: String
        )
    }
}