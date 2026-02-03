package exchange_api.bybit

import org.knowm.xchange.utils.DigestUtils
import java.net.URLDecoder
import java.net.URLEncoder
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
            val value = map[key].toString()
            sb.append(key)
                .append("=")
                .append(normalizeParameterValue(value))
                .append("&")
        }
        sb.deleteCharAt(sb.length - 1)
        return sb
    }
    
    /**
     * Normalizes parameter value to match what OkHttp actually sends in the URL.
     * This handles the discrepancy between local and Docker environments.
     */
    private fun normalizeParameterValue(value: String): String {
        return try {
            // If value contains % characters, it might be already encoded
            if (value.contains("%")) {
                // Check if it's double-encoded by trying to decode twice
                val decoded = URLDecoder.decode(value, "UTF-8")
                if (decoded != value && decoded.contains(":") && decoded.contains(",")) {
                    // It was encoded, now encode it properly for signature
                    URLEncoder.encode(decoded, "UTF-8")
                } else {
                    // It was not double-encoded, encode as-is
                    URLEncoder.encode(value, "UTF-8")
                }
            } else {
                // Not encoded, encode it
                URLEncoder.encode(value, "UTF-8")
            }
        } catch (e: Exception) {
            // If anything fails, fall back to simple encoding
            URLEncoder.encode(value, "UTF-8")
        }
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