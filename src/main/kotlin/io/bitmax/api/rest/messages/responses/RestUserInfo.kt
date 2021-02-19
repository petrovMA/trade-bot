package io.bitmax.api.rest.messages.responses

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestUserInfo(
        val code: Long = 0,
        val data: Data
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Data(
        val email: String,
        val accountGroup: Int = 0,
        val viewPermission: Boolean
)
