package io.bybit.api.rest.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.bybit.api.Authorization
import io.bybit.api.rest.response.*
import io.bybit.api.rest.response.cancel_order.CancelOrderRequest
import io.bybit.api.rest.response.cancel_order.CancelOrderResponse
import io.bybit.api.rest.response.create_order.CreateOrderRequest
import io.bybit.api.rest.response.create_order.CreateOrderResponse
import io.bybit.api.rest.response.order_list.OrderListResponse
import io.bybit.api.rest.response.time.TimeResponse
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import utils.mapper.Mapper
import java.io.IOException
import java.time.ZonedDateTime
import java.util.*


class ByBitRestApiClient(private val apikey: String, private val secret: String) {
    private val url = "api.bybit.com"
    private val version = "v5"
    private val client: OkHttpClient = OkHttpClient()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val TIMESTAMP = ZonedDateTime.now().toInstant().toEpochMilli().toString()
    private val RECV_WINDOW = "10000"
    private fun builder() = HttpUrl.Builder().scheme("https").host(url).addPathSegment(version)

    private val log = KotlinLogging.logger {}

    fun getOrderBook(pair: String, category: String = "spot"): OrderBookResponse.Result =
        executeRequest<OrderBookResponse>(
            Request.Builder().url(
                builder()
                    .addPathSegment("market")
                    .addPathSegment("orderbook")
                    .addQueryParameter("symbol", pair)
                    .addQueryParameter("category", category)
                    .build()
            ).build()
        ).result

    fun getOpenOrders(category: String = "spot", symbol: String? = null, coin: String? = null): OpenOrders.Result {

        val params = TreeMap<String, String>().apply {
            put("category", category)
            symbol?.let { put("symbol", it) }
            coin?.let { put("symbol", it) }
        }

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("realtime")
            addQueryParameter("category", category)
            symbol?.let { addQueryParameter("symbol", it) }
            coin?.let { addQueryParameter("coin", it) }
        }
            .build()

        val request: Request = Request.Builder().url(builder)
            .apply { headers(params).forEach { (key, value) -> addHeader(key, value) } }
            .addHeader("Content-Type", "application/json")
            .build()

        return executeRequest<OpenOrders>(request).result
    }

    fun getKline(
        symbol: String,
        category: String = "spot",
        interval: String,
        start: Long? = null,
        end: Long? = null,
        limit: Long? = null
    ): KlineResponse {
        val builder = builder().apply {
            addPathSegment("market")
            addPathSegment("kline")
            addQueryParameter("category", category)
            addQueryParameter("symbol", symbol)
            addQueryParameter("interval", interval)
            start?.let { addQueryParameter("start", it.toString()) }
            end?.let { addQueryParameter("end", it.toString()) }
            limit?.let { addQueryParameter("limit", it.toString()) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request)
    }

    fun getBalance(coin: String? = null): BalanceResponse {

        val requestParams = createMapParams(TreeMap<String, String>().apply { coin?.let { put("coin", it) } })

        val builder = builder().apply {
            addPathSegment("asset")
            addPathSegment("transfer")
            addPathSegment("query-account-coins-balance")
            requestParams.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request)
    }

    fun getOrderList(
        symbol: String,
        orderStatus: String? = null,
        direction: String? = null,
        limit: String? = null,
        cursor: String? = null
    ): OrderListResponse {

        val requestParams = createMapParams(TreeMap<String, String>().apply {
            put("symbol", symbol)
            orderStatus?.let { put("order_status", it) }
            direction?.let { put("direction", it) }
            limit?.let { put("limit", it) }
            cursor?.let { put("cursor", it) }
        })

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("list")
            requestParams.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest(request)
    }

    fun orderCreate(
        side: String,
        symbol: String,
        orderType: String,
        qty: String,
        timeInForce: String
    ): CreateOrderResponse {

        val requestParams = createMapParams(TreeMap<String, String>().apply {
            put("side", side)
            put("symbol", symbol)
            put("order_type", orderType)
            put("qty", qty)
            put("time_in_force", timeInForce)
        })

        val requestBody = CreateOrderRequest(
            side = side,
            symbol = symbol,
            order_type = orderType,
            qty = qty,
            time_in_force = timeInForce,
            timestamp = requestParams["timestamp"]!!,
            sign = requestParams["sign"]!!,
            api_key = requestParams["api_key"]!!,
        )

        val body = body(requestBody)

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("create")
        }.build()

        return executeRequest(Request.Builder().url(builder).post(body).build())
    }

    fun orderCancel(symbol: String, orderId: String? = null, orderLinkId: String? = null): CancelOrderResponse {

        if (orderId.isNullOrEmpty() && orderLinkId.isNullOrEmpty())
            throw java.lang.RuntimeException("'orderId' or 'orderLinkId' must not be null!")

        val requestParams = createMapParams(TreeMap<String, String>().apply {
            put("symbol", symbol)
            orderId?.let { put("order_id", it) }
            orderLinkId?.let { put("order_link_id", it) }
        })

        val requestBody = CancelOrderRequest(
            symbol = symbol,
            order_id = orderId,
            order_link_id = orderLinkId,
            timestamp = requestParams["timestamp"]!!,
            sign = requestParams["sign"]!!,
            api_key = requestParams["api_key"]!!,
        )

        val body = body(requestBody)

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("cancel")
        }.build()

        return executeRequest(Request.Builder().url(builder).post(body).build())
    }

    fun getTime(): TimeResponse {
        val request: Request = Request.Builder().url(builder().addPathSegment("time").build()).build()
        return executeRequest<TimeResponse>(request)
    }

    private inline fun <reified T : Response> executeRequest(request: Request): T = try {
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
        val resp = Mapper.asObject<T>(respBody)
        if ((resp.retCode == 0L || resp.retCode == null) && (resp.ret_code == 0L || resp.ret_code == null)) resp
        else throw ByBitRestException(resp.retMsg ?: resp.ret_msg!!, resp.retCode ?: resp.ret_code!!)
    } catch (t: Throwable) {
        t.printStackTrace()
        log.error("Error while executing request: $request", t)
        throw t
    }

    private fun body(obj: Any) =
        objectMapper.writeValueAsString(obj).toRequestBody("application/json".toMediaTypeOrNull()!!)

    private fun createMapParams(params: TreeMap<String, String> = TreeMap()): TreeMap<String, String> = params.apply {
        put("api_key", apikey)
        put("timestamp", System.currentTimeMillis().toString())
        put("sign", Authorization.signForRest(this, secret))
    }

    private fun headers(params: TreeMap<String, String> = TreeMap()) = params.apply {
        val sign = Authorization.genGetSign(params, TIMESTAMP, apikey, RECV_WINDOW, secret)
        put("X-BAPI-API-KEY", apikey)
        put("X-BAPI-SIGN", sign)
        put("X-BAPI-SIGN-TYPE", "2")
        put("X-BAPI-TIMESTAMP", TIMESTAMP)
        put("X-BAPI-RECV-WINDOW", RECV_WINDOW)
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