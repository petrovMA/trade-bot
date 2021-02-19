package io.bitmax.api

import io.bitmax.api.websocket.messages.requests.WebSocketAuth
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Authorization(private val apiKey: String, secretKey: String?) {
    private val hmac = Mac.getInstance("HmacSHA256")
    private val hmacKey = Base64.getDecoder().decode(secretKey)
    private val keySpec: SecretKeySpec = SecretKeySpec(hmacKey, "HmacSHA256")

    init {
        hmac.init(keySpec)
    }

    /**
     * @return authorization headers
     * @param path path for generating specific signature
     * @param timestamp milliseconds since UNIX epoch in UTC
     */
    fun getHeaderMap(path: String, timestamp: Long): Map<String, String> {
        val headers: MutableMap<String, String> = HashMap()
        headers["x-auth-key"] = apiKey
        headers["x-auth-signature"] = generateSig(path, timestamp)
        headers["x-auth-timestamp"] = timestamp.toString()
        return headers
    }

    /**
     * @return authorization headers
     * @param timestamp milliseconds since UNIX epoch in UTC
     */
    fun getAuthSocketMsg(timestamp: Long): WebSocketAuth {
        return WebSocketAuth("auth", timestamp, apiKey, generateSocketSig(timestamp))
    }

    /**
     * @return signature, signed using sha256 using the base64-decoded secret key
     */
    private fun generateSig(url: String, timestamp: Long): String =
        String(Base64.getEncoder().encode(hmac!!.doFinal("$timestamp+$url".toByteArray(StandardCharsets.UTF_8))))

    private fun generateSocketSig(timestamp: Long): String =
        String(Base64.getEncoder().encode(hmac!!.doFinal("$timestamp+stream".toByteArray(StandardCharsets.UTF_8))))
}