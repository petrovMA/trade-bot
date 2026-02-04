package exchange_api.extended.rest.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import exchange_api.extended.rest.response.*
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit


class ExtendedRestApiClient(private val apiKey: String) {

    private val baseHost = "api.starknet.extended.exchange"
    private val basePath = "api/v1"
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val log = KotlinLogging.logger {}

    private fun urlBuilder() = HttpUrl.Builder()
        .scheme("https")
        .host(baseHost)
        .addPathSegments(basePath)

    private fun buildAuthHeaders(builder: Request.Builder): Request.Builder = builder
        .addHeader("X-Api-Key", apiKey)
        .addHeader("User-Agent", "trade-bot/1.0")
        .addHeader("Content-Type", "application/json")

    // ===== Public endpoints =====

    fun getMarkets(market: String? = null): List<ExtendedMarket> {
        val url = urlBuilder().apply {
            addPathSegment("markets")
            market?.let { addQueryParameter("market", it) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getOrderBook(market: String): ExtendedOrderBookResponse {
        val url = urlBuilder().apply {
            addPathSegment("orderbook")
            addQueryParameter("market", market)
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getTrades(market: String, limit: Int? = null): List<ExtendedTrade> {
        val url = urlBuilder().apply {
            addPathSegment("trades")
            addQueryParameter("market", market)
            limit?.let { addQueryParameter("limit", it.toString()) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getCandles(
        market: String,
        candleType: String = "trades",
        interval: String,
        limit: Int? = null,
        endTime: Long? = null
    ): List<ExtendedCandle> {
        val url = urlBuilder().apply {
            addPathSegment("candles")
            addQueryParameter("market", market)
            addQueryParameter("candleType", candleType)
            addQueryParameter("interval", interval)
            limit?.let { addQueryParameter("limit", it.toString()) }
            endTime?.let { addQueryParameter("endTime", it.toString()) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    // ===== Private endpoints =====

    fun getBalance(): ExtendedBalance {
        val url = urlBuilder().apply {
            addPathSegment("balance")
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getPositions(market: String? = null, side: String? = null): List<ExtendedPosition> {
        val url = urlBuilder().apply {
            addPathSegment("positions")
            market?.let { addQueryParameter("market", it) }
            side?.let { addQueryParameter("side", it) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getOpenOrders(market: String? = null, type: String? = null, side: String? = null): List<ExtendedOrder> {
        val url = urlBuilder().apply {
            addPathSegment("orders")
            addQueryParameter("status", "open")
            market?.let { addQueryParameter("market", it) }
            type?.let { addQueryParameter("type", it) }
            side?.let { addQueryParameter("side", it) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getOrdersHistory(market: String? = null): List<ExtendedOrder> {
        val url = urlBuilder().apply {
            addPathSegment("orders")
            addPathSegment("history")
            market?.let { addQueryParameter("market", it) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun getOrderById(orderId: String): ExtendedOrder? {
        val url = urlBuilder().apply {
            addPathSegment("orders")
            addPathSegment(orderId)
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return try {
            executeRequest(request)
        } catch (e: Exception) {
            log.warn("Failed to get order by ID $orderId: ${e.message}")
            null
        }
    }

    fun getLeverage(market: String? = null): List<ExtendedLeverage> {
        val url = urlBuilder().apply {
            addPathSegment("leverage")
            market?.let { addQueryParameter("market", it) }
        }.build()

        val request = Request.Builder().url(url)
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun updateLeverage(market: String, leverage: String) {
        val url = urlBuilder().apply {
            addPathSegment("leverage")
        }.build()

        val body = gson.toJson(ExtendedUpdateLeverageRequest(market, leverage))

        val request = Request.Builder().url(url)
            .put(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .apply { buildAuthHeaders(this) }
            .build()

        executeRequest<Any>(request)
    }

    fun createOrder(orderRequest: ExtendedCreateOrderRequest): ExtendedCreateOrderResponse {
        val url = urlBuilder().apply {
            addPathSegment("orders")
        }.build()

        val body = gson.toJson(orderRequest)

        val request = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .apply { buildAuthHeaders(this) }
            .build()

        return executeRequest(request)
    }

    fun cancelOrder(orderId: String): Boolean {
        val url = urlBuilder().apply {
            addPathSegment("orders")
            addPathSegment(orderId)
        }.build()

        val request = Request.Builder().url(url)
            .delete()
            .apply { buildAuthHeaders(this) }
            .build()

        return try {
            executeRequest<Any>(request)
            true
        } catch (e: Exception) {
            log.error("Failed to cancel order $orderId: ${e.message}")
            false
        }
    }

    fun cancelOrderByExternalId(externalId: String): Boolean {
        val url = urlBuilder().apply {
            addPathSegment("orders")
            addPathSegment("external")
            addPathSegment(externalId)
        }.build()

        val request = Request.Builder().url(url)
            .delete()
            .apply { buildAuthHeaders(this) }
            .build()

        return try {
            executeRequest<Any>(request)
            true
        } catch (e: Exception) {
            log.error("Failed to cancel order by external ID $externalId: ${e.message}")
            false
        }
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        val requestStartTime = System.currentTimeMillis()

        log.debug {
            buildString {
                appendLine("=== Extended REST API Request ===")
                appendLine("URL: ${request.url}")
                appendLine("Method: ${request.method}")
                appendLine("Headers:")
                request.headers.forEach { (name, value) ->
                    if (name.contains("Key", ignoreCase = true) || name.contains("Sign", ignoreCase = true)) {
                        appendLine("  $name: [HIDDEN]")
                    } else {
                        appendLine("  $name: $value")
                    }
                }
                appendLine("=================================")
            }
        }

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body from ${request.url}")

            val requestDuration = System.currentTimeMillis() - requestStartTime

            log.debug {
                buildString {
                    appendLine("=== Extended REST API Response ===")
                    appendLine("URL: ${request.url}")
                    appendLine("Status: ${response.code}")
                    appendLine("Duration: ${requestDuration}ms")
                    appendLine("Response Body:")
                    appendLine(responseBody)
                    appendLine("==================================")
                }
            }

            if (!response.isSuccessful) {
                throw ExtendedRestException(
                    "Extended API error: HTTP ${response.code} - $responseBody",
                    response.code.toLong()
                )
            }

            log.info("Extended API call successful: ${request.method} ${request.url.encodedPath} (${requestDuration}ms)")

            val type = object : TypeToken<T>() {}.type
            return gson.fromJson(responseBody, type)
        } catch (t: Throwable) {
            val requestDuration = System.currentTimeMillis() - requestStartTime
            log.error("Error executing Extended API request: ${request.method} ${request.url.encodedPath} (${requestDuration}ms)", t)
            throw t
        }
    }
}

class ExtendedRestException(message: String, val code: Long) : RuntimeException(message)
