package io.bybit.api.rest.response;

class ByBitRestException(msg: String, val code: Long) : RuntimeException(msg)
