package io.bybit.api.rest.response

class PositionResponse(
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
        val category: String,
        val list: List<Position>,
        val nextPageCursor: String
    ) {
        data class Position(
            val adlRankIndicator: Int,
            val autoAddMargin: Int,
            val avgPrice: String,
            val bustPrice: String,
            val createdTime: String,
            val cumRealisedPnl: String,
            val curRealisedPnl: String,
            val isReduceOnly: Boolean,
            val leverage: String,
            val leverageSysUpdatedTime: String,
            val liqPrice: String,
            val markPrice: String,
            val mmrSysUpdateTime: String,
            val positionBalance: String,
            val positionIM: String,
            val positionIdx: Int,
            val positionMM: String,
            val positionStatus: String,
            val positionValue: String,
            val riskId: Int,
            val riskLimitValue: String,
            val seq: Long,
            val side: String,
            val size: String,
            val stopLoss: String,
            val symbol: String,
            val takeProfit: String,
            val tpslMode: String,
            val tradeMode: Int,
            val trailingStop: String,
            val unrealisedPnl: String,
            val updatedTime: String
        )
    }
}