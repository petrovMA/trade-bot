package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestOrderDetails(
        val code: Int = 0,
        val accountId: String,
        val ac: String,
        val data: RestOrderDetailsData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestOrderDetailsData(
        val seqNum: Long,
        val orderId: String,
        val symbol: String,
        val orderType: String,
        val lastExecTime: Long,
        val price: String,
        val orderQty: String,
        val side: String,
        val status: String,
        val avgPx: String,
        val cumFilledQty: String,
        val stopPrice: String,
        val errorCode: String,
        val cumFee: String,
        val feeAsset: String,
        val execInst: String
)