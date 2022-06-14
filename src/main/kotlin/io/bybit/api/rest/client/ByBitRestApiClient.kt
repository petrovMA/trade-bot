package io.bybit.api.rest.client

import io.bitmax.api.Mapper
import io.bybit.api.rest.messages.kline.KlineResponse
import io.bybit.api.rest.messages.order_book.OrderBookResponse
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException

class ByBitRestApiClient {
    private val url = "api-testnet.bybit.com"
    private val public = "public"
    private val version = "v2"
    private val private = "private"
    private val client: OkHttpClient = OkHttpClient()

    private val requestPrivate = HttpUrl.Builder().scheme("https").host(url)
        .addPathSegment(version).addPathSegment(private)
    private val requestPublic = HttpUrl.Builder().scheme("https").host(url)
        .addPathSegment(version).addPathSegment(public)

    private val log = KotlinLogging.logger {}

    fun getOrderBook(pair: String): OrderBookResponse {
        val builder = requestPublic
            .addPathSegment("orderBook")
            .addPathSegment("L2")
            .addQueryParameter("symbol", pair)
            .build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request, OrderBookResponse::class.java)
    }

    fun getKline(pair: String, interval: INTERVAL, from: Long, limit: Long? = null): KlineResponse {
        val builder = requestPublic.apply {
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