package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestBalances(
        var code:Int = 0,
        var data: List<RestBalance> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestBalance(
        var asset: String,
        var assetName: String,
        var totalBalance: String,
        var availableBalance: String
)