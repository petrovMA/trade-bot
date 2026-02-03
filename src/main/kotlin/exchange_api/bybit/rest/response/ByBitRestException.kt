package exchange_api.bybit.rest.response;

class ByBitRestException(msg: String, val code: Long) : RuntimeException(msg)
