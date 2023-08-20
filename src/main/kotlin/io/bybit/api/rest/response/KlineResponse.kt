package io.bybit.api.rest.response

import io.bybit.api.rest.response.Response

class KlineResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Any
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time) {
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
}