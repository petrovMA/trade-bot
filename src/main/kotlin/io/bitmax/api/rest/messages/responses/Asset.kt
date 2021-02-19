package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Asset(
        val assetCode: String,
        val assetName: String,
        val precisionScale: Int? = null,
        val nativeScale:Int? = null,
        val withdrawalFee:Double? = null,
        val minWithdrawalAmt:Double? = null,
        val status:String
)