package io.bybit.api

import org.knowm.xchange.utils.DigestUtils
import java.time.ZonedDateTime
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Authorization {
    private val TIMESTAMP = ZonedDateTime.now().toInstant().toEpochMilli().toString()

    fun signForWebSocket(params: String, secret: String): String {
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secret_key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secret_key)
        return DigestUtils.bytesToHex(sha256_HMAC.doFinal(params.toByteArray()))
    }

    fun genGetSign(params: Map<String, Any>, timestamp: String, apiKey: String, recvWindow: String, apiSecret: String): String {
        val sb = genQueryStr(params)
        val queryStr = timestamp + apiKey + recvWindow + sb
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secret_key = SecretKeySpec(apiSecret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secret_key)
        return bytesToHex(sha256_HMAC.doFinal(queryStr.toByteArray()))
    }

    private fun genQueryStr(map: Map<String, Any>): java.lang.StringBuilder {
        val keySet = map.keys
        val iter = keySet.iterator()
        val sb = java.lang.StringBuilder()
        while (iter.hasNext()) {
            val key = iter.next()
            sb.append(key)
                .append("=")
                .append(map[key])
                .append("&")
        }
        sb.deleteCharAt(sb.length - 1)
        return sb
    }

    fun signForRest(params: TreeMap<String, Any>, secret: String): String {
        val keySet: Set<String> = params.keys
        val iter = keySet.iterator()
        val sb = StringBuilder()
        while (iter.hasNext()) {
            val key = iter.next()
            sb.append(key + "=" + params[key])
            sb.append("&")
        }
        sb.deleteCharAt(sb.length - 1)
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secret_key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secret_key)
        return bytesToHex(sha256_HMAC.doFinal(sb.toString().toByteArray()))
    }

    private fun bytesToHex(hash: ByteArray) = StringBuffer().also { hexString ->
        for (i in hash.indices) {
            val hex = Integer.toHexString(0xff and hash[i].toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
    }.toString()
}