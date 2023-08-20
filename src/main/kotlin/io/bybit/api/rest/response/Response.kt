package io.bybit.api.rest.response

open class Response(
    val retCode: Long?,
    val ret_code: Long?,
    val retMsg: String?,
    val ret_msg: String?,
    val retExtInfo: Any?,
    val time: Long
)