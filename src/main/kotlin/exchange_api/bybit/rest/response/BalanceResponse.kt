package exchange_api.bybit.rest.response

import exchange_api.bybit.rest.response.Response

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