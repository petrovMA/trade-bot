package io.bybit.api.rest.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.bitmax.api.Mapper
import io.bybit.api.rest.messages.balance.BalanceResponse
import io.bybit.api.rest.messages.create_order.CreateOrderRequest
import io.bybit.api.rest.messages.create_order.CreateOrderResponse
import io.bybit.api.rest.messages.kline.KlineResponse
import io.bybit.api.rest.messages.order_book.OrderBookResponse
import io.bybit.api.rest.messages.time.TimeResponse
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class ByBitRestApiClient(private val apikey: String, private val secret: String) {
    private val url = "api.bybit.com"
    private val public = "public"
    private val version = "v2"
    private val private = "private"
    private val client: OkHttpClient = OkHttpClient()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val JSON: MediaType? = "application/json".toMediaTypeOrNull()

    private val log = KotlinLogging.logger {}

    fun getOrderBook(pair: String): OrderBookResponse {
        val builder = public()
            .addPathSegment("orderBook")
            .addPathSegment("L2")
            .addQueryParameter("symbol", pair)
            .build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request, OrderBookResponse::class.java)
    }

    fun getKline(pair: String, interval: INTERVAL, from: Long, limit: Long? = null): KlineResponse {
        val builder = public().apply {
            addPathSegment("kline")
            addPathSegment("list")
            addQueryParameter("symbol", pair)
            addQueryParameter("interval", interval.time)
            addQueryParameter("from", from.toString())
            limit?.let { addQueryParameter("limit", it.toString()) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request, KlineResponse::class.java)
    }

    fun getBalance(coin: String? = null): BalanceResponse {

        val requestParams = createMapParams(TreeMap<String, String>().apply { coin?.let { put("coin", it) } })

        val builder = private().apply {
            addPathSegment("wallet")
            addPathSegment("balance")
            requestParams.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request, BalanceResponse::class.java)
    }

    fun orderCreate(
        side: String,
        symbol: String,
        order_type: String,
        qty: String,
        time_in_force: String
    ): CreateOrderResponse {

        val requestParams = createMapParams(TreeMap<String, String>().apply {
            put("side", side)
            put("symbol", symbol)
            put("order_type", order_type)
            put("qty", qty)
            put("time_in_force", time_in_force)
        })

        val requestBody = CreateOrderRequest(
            side = side,
            symbol = symbol,
            order_type = order_type,
            qty = qty,
            time_in_force = time_in_force,
            timestamp = requestParams["timestamp"]!!,
            sign = requestParams["sign"]!!,
            api_key = requestParams["api_key"]!!,
        )

        val body = body(requestBody)

        val builder = private().apply {
            addPathSegment("order")
            addPathSegment("create")
        }.build()

        return executeRequest(Request.Builder().url(builder).post(body).build(), CreateOrderResponse::class.java)
    }

    fun getTime(): TimeResponse {
        val request: Request = Request.Builder().url(public().addPathSegment("time").build()).build()
        return executeRequest(request, TimeResponse::class.java)
    }

    private fun <T> executeRequest(request: Request, clazz: Class<T>): T = try {
        log.trace {
            try {
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val body = buffer.readUtf8()

                "Request:\n$request" + (if (body.isNotBlank()) "\nBody:\n$body" else "")
            } catch (e: IOException) {
                log.warn("Can't read request for logging")
            }
        }

        val respBody = client.newCall(request).execute().body!!.string()
        log.trace("Response:\n$respBody")
        Mapper.asObject(respBody, clazz)
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

    private fun private() = HttpUrl.Builder().scheme("https").host(url)
        .addPathSegment(version).addPathSegment(private)

    private fun public() = HttpUrl.Builder().scheme("https").host(url)
        .addPathSegment(version).addPathSegment(public)

    private fun body(obj: Any) = objectMapper.writeValueAsString(obj).toRequestBody(JSON)

    private fun createMapParams(params: TreeMap<String, String> = TreeMap()): TreeMap<String, String> = params.apply {
        put("api_key", apikey)
        put("timestamp", System.currentTimeMillis().toString())
        put("sign", sign(this, secret))
    }


    private fun sign(params: TreeMap<String, String>, secret: String): String {
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

    enum class INTERVAL(val time: String) {
        ONE_MINUTE("1"),
        THREE_MINUTES("3"),
        FIVE_MINUTES("5"),
        FIFTEEN_MINUTES("15"),
        HALF_HOURLY("30"),
        HOURLY("60"),
        TWO_HOURLY("120"),
        FOUR_HOURLY("240"),
        SIX_HOURLY("360"),
        TWELVE_HOURLY("720"),
        DAILY("D"),
        WEEKLY("W"),
        MONTHLY("M");
    }
}