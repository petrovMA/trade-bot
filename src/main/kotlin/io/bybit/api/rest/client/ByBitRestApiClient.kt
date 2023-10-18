package io.bybit.api.rest.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.bybit.api.Authorization
import io.bybit.api.rest.response.*
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
    private val RECV_WINDOW = "10000"
    private fun builder() = HttpUrl.Builder().scheme("https").host(url).addPathSegment(version)
    private fun timestamp() = ZonedDateTime.now().toInstant().toEpochMilli().toString()

    private val log = KotlinLogging.logger {}

    fun getOrderBook(pair: String, category: String = "spot") = executeRequest<OrderBookResponse>(
        Request.Builder().url(
            builder()
                .addPathSegment("market")
                .addPathSegment("orderbook")
                .addQueryParameter("symbol", pair)
                .addQueryParameter("category", category)
                .build()
        ).build()
    ).result


    fun getOpenOrders(
        category: String = "spot",
        symbol: String? = null,
        baseCoin: String? = null,
        settleCoin: String? = null,
        orderId: String? = null
    ): OpenOrders.Result {

        val params = TreeMap<String, String>().apply {
            put("category", category)
            symbol?.let { put("symbol", it) }
            baseCoin?.let { put("baseCoin", it) }
            settleCoin?.let { put("settleCoin", it) }
            orderId?.let { put("orderId", it) }
        }

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("realtime")
            params.forEach(::addQueryParameter)
        }
            .build()

        val request: Request = Request.Builder().url(builder)
            .apply { headers(params).forEach(::addHeader) }
            .build()

        return executeRequest<OpenOrders>(request).result
    }

    fun getKline(
        symbol: String,
        category: String = "spot",
        interval: INTERVAL,
        start: Long? = null,
        end: Long? = null,
        limit: Long? = null
    ): KlineResponse.Result {
        val builder = builder().apply {
            addPathSegment("market")
            addPathSegment("kline")
            addQueryParameter("category", category)
            addQueryParameter("symbol", symbol)
            addQueryParameter("interval", interval.time)
            start?.let { addQueryParameter("start", it.toString()) }
            end?.let { addQueryParameter("end", it.toString()) }
            limit?.let { addQueryParameter("limit", it.toString()) }
        }.build()

        val request: Request = Request.Builder().url(builder).build()
        return executeRequest<KlineResponse>(request).result
    }

    fun getBalance(
        accountType: String = "SPOT",
        coin: String? = null,
        memberId: String? = null
    ): BalanceResponse.Result {

        val params = TreeMap<String, String>().apply {
            put("accountType", accountType)
            coin?.let { put("coin", it) }
            memberId?.let { put("memberId", it) }
        }

        val builder = builder().apply {
            addPathSegment("asset")
            addPathSegment("transfer")
            addPathSegment("query-account-coins-balance")
            params.forEach(::addQueryParameter)
        }.build()

        val request: Request = Request.Builder().url(builder)
            .apply { headers(params).forEach(::addHeader) }
            .build()

        return executeRequest<BalanceResponse>(request).result
    }

    fun newOrder(
        side: String,
        category: String = "spot",
        symbol: String,
        orderType: String,
        qty: String,
        isLeverage: String? = null,
        price: String? = null,
        triggerDirection: String? = null,
        orderFilter: String? = null,
        triggerPrice: String? = null,
        triggerBy: String? = null,
        orderIv: String? = null,
        timeInForce: String? = null,
        positionIdx: Int? = null
    ): CreateOrderResponse.Result {

        val params = createMapParams(TreeMap<String, String>().apply {
            put("side", side)
            put("symbol", symbol)
            put("category", category)
            put("orderType", orderType)
            put("qty", qty)
            isLeverage?.let { put("isLeverage", it) }
            price?.let { put("price", it) }
            triggerDirection?.let { put("triggerDirection", it) }
            orderFilter?.let { put("orderFilter", it) }
            triggerPrice?.let { put("triggerPrice", it) }
            triggerBy?.let { put("triggerBy", it) }
            orderIv?.let { put("orderIv", it) }
            timeInForce?.let { put("timeInForce", it) }
            positionIdx?.let { put("positionIdx", it.toString()) }
        })

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("create")
        }.build()

        return executeRequest<CreateOrderResponse>(Request.Builder().url(builder).post(body(params)).build()).result
    }

    fun orderCancelAll(
        category: String = "spot",
        symbol: String? = null,
        baseCoin: String? = null,
        settleCoin: String? = null,
        orderFilter: String? = null
    ): CancelAllResponse.Result {

        val params = createMapParams(TreeMap<String, String>().apply {
            put("category", category)
            symbol?.let { put("symbol", it) }
            baseCoin?.let { put("baseCoin", it) }
            settleCoin?.let { put("settleCoin", it) }
            orderFilter?.let { put("orderFilter", it) }
        })

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("cancel-all")
        }.build()

        return executeRequest<CancelAllResponse>(Request.Builder().url(builder).post(body(params)).build()).result
    }

    fun orderCancel(
        category: String = "spot",
        symbol: String,
        orderId: String? = null,
        orderLinkId: String? = null,
        orderFilter: String? = null
    ): CancelResponse.Result {

        val requestParams = createMapParams(TreeMap<String, String>().apply {
            put("category", category)
            put("symbol", symbol)
            orderId?.let { put("orderId", it) }
            orderLinkId?.let { put("orderLinkId", it) }
            orderFilter?.let { put("orderFilter", it) }
        })

        val builder = builder().apply {
            addPathSegment("order")
            addPathSegment("cancel")
        }.build()

        return executeRequest<CancelResponse>(Request.Builder().url(builder).post(body(requestParams)).build()).result
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
        val timestamp = timestamp()
        val sign = Authorization.genGetSign(params, timestamp, apikey, RECV_WINDOW, secret)
        put("X-BAPI-API-KEY", apikey)
        put("X-BAPI-SIGN", sign)
        put("X-BAPI-SIGN-TYPE", "2")
        put("X-BAPI-TIMESTAMP", timestamp)
        put("X-BAPI-RECV-WINDOW", RECV_WINDOW)
        put("Content-Type", "application/json")
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