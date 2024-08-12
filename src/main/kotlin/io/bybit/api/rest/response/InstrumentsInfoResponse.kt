package io.bybit.api.rest.response

class InstrumentsInfoResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Result
) : Response(
    retCode = retCode,
    retMsg = retMsg,
    ret_code = ret_code,
    ret_msg = ret_msg,
    retExtInfo = retExtInfo,
    time = time
) {
    data class Result(val category: String, val list: List<Row>) {
        data class Row(
            val symbol: String,
            val contractType: String,
            val status: String,
            val baseCoin: String,
            val quoteCoin: String
        )
    }
}