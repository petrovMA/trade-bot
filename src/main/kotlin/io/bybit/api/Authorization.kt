package io.bybit.api

import org.knowm.xchange.utils.DigestUtils
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Authorization {

    fun signForWebSocket(params: String, secret: String): String {
        val sha256_HMAC = Mac.getInstance("HmacSHA256")
        val secret_key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        sha256_HMAC.init(secret_key)
        return DigestUtils.bytesToHex(sha256_HMAC.doFinal(params.toByteArray()))
    }

    fun signForRest(params: TreeMap<String, String>, secret: String): String {
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