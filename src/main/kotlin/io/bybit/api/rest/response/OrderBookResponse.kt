package io.bybit.api.rest.response

import com.google.gson.annotations.SerializedName

class OrderBookResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Result
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time) {
    data class Result(
        @SerializedName("s") val symbol: String,
        @SerializedName("b") val bids: List<List<String>>,
        @SerializedName("a") val asks: List<List<String>>,
        @SerializedName("ts") val timestamp: Long,
        @SerializedName("u") val updateId: Long
    )
}