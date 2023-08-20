package io.bybit.api.rest.response.cancel_order

import io.bybit.api.rest.response.Response

class CancelOrderResponse(
    retCode: Long?,
    retMsg: String?,
    ret_code: Long?,
    ret_msg: String?,
    retExtInfo: Any?,
    time: Long,
    val result: Any
) : Response(retCode = retCode, retMsg = retMsg, ret_code = ret_code, ret_msg = ret_msg, retExtInfo = retExtInfo, time = time)