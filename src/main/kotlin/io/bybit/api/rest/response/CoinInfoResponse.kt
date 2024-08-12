package io.bybit.api.rest.response

class CoinInfoResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Result
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time) {
    data class Result(
        val rows: List<Row>
    ) {
        data class Row(
            val chains: List<Chain>,
            val coin: String,
            val name: String,
            val remainAmount: String
        ) {
            data class Chain(
                val chain: String,
                val chainDeposit: String,
                val chainType: String,
                val chainWithdraw: String,
                val confirmation: String,
                val depositMin: String,
                val minAccuracy: String,
                val withdrawFee: String,
                val withdrawMin: String,
                val withdrawPercentageFee: String
            )
        }
    }
}