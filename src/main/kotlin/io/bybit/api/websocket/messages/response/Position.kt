package io.bybit.api.websocket.messages.response

data class Position(
    val creationTime: Long,
    val `data`: List<Data>,
    val id: String,
    val topic: String
) {
    data class Data(
        val adlRankIndicator: Int,
        val autoAddMargin: Int,
        val bustPrice: String,
        val category: String,
        val createdTime: String,
        val cumRealisedPnl: String,
        val entryPrice: String,
        val isReduceOnly: Boolean,
        val leverage: String,
        val leverageSysUpdatedTime: String,
        val liqPrice: String,
        val markPrice: String,
        val mmrSysUpdatedTime: String,
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