package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestOpenOrdersList(
    val code: Int = 0,
    val status: String? = null,
    val email: String? = null,
    val data: List<RestOrderDetailsData> = emptyList()
)