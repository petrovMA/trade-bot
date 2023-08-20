package io.bybit.api.rest.response.time

import io.bybit.api.rest.response.Response

class TimeResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val ext_code: String,
    val ext_info: String,
    val result: Map<Any, Any>,
    val time_now: String
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time)