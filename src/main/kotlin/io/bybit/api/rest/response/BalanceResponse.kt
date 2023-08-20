package io.bybit.api.rest.response

class BalanceResponse(
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
    data class Result(
        val accountType: String,
        val balance: List<Balance>,
        val memberId: String
    ) {
        data class Balance(
            val bonus: String,
            val coin: String,
            val transferBalance: String,
            val walletBalance: String
        )
    }
}