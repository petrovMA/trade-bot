package io.bybit.api.rest.response

class KlineResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Result
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time) {
    data class Result(
        val symbol: String,
        val category: String,
        val list: List<List<String>>
    )
}