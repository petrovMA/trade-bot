package io.bitmax.api.rest.client

import io.bitmax.api.Mapper.asObject
import io.bitmax.api.rest.messages.requests.RestCancelOrderRequest
import io.bitmax.api.rest.messages.requests.RestPlaceOrderRequest
import io.bitmax.api.rest.messages.responses.*
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException

/**
 * BitMaxRestApiClient public restClient for unauthorized users.
 */
open class BitMaxRestApiClient {
    val url = "https://bitmax.io/"
    val api = "api/pro/v1/"
    private val pathBars = "barhist"
    private val pathProducts = "products"
    val assets = "assets"
    val pathInfo = "info"
    val pathOrders = "order/open"
    val cash = "cash/"
    val balanceUrl = "balance"
    val pathOrder = "order"
    val depth = "depth"
    val pathOrderStatus = "order/status"
    val client: OkHttpClient = OkHttpClient()
    val requestBuilder = Request.Builder()
    private val log = KotlinLogging.logger {}

    /**
     * @return history of bars
     * @param symbol trade pair
     * @param interval bars interval
     * @param limit max bars count
     */
    fun getCandlestickBars(symbol: String, interval: BitmaxInterval, limit: Int): Array<RestBarHist> {
        val between = getFrom(interval, limit)
        val params = "?symbol=" + symbol + "&interval=" + interval + "&from=" + between[0] + "&to=" + between[1]
        return try {
            executeRequest(
                requestBuilder
                    .url(url + api + pathBars + params)
                    .get()
                    .build(), Array<RestBarHist>::class.java
            )
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    /**
     * @return order book
     */
    fun getOrderBook(pair: String): OrderBook {
        val builder = requestBuilder.url("$url$api$depth?symbol=$pair").get()
        return executeRequest(builder.build(), OrderBook::class.java)
    }

    /**
     * @return Each market summary data record contains current information about every product.
     */
    val products: Array<RestProduct>
        get() = try {
            val result = client.newCall(
                requestBuilder
                    .url(url + api + pathProducts)
                    .get()
                    .build()
            ).execute()
            asObject(result.body!!.string(), Array<RestProduct>::class.java)
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    val allAssets: Array<Asset>
        get() = try {
            executeRequest(
                requestBuilder
                    .url(url + api + assets)
                    .get()
                    .build(), Array<Asset>::class.java
            )
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    open val userInfo: RestUserInfo?
        get() = null
    open val balance: RestBalances?
        get() = null
    open val openOrders: RestOpenOrdersList?
        get() = null

    open fun getOrder(orderId: String): RestOrderDetails? {
        return null
    }

    open fun placeOrder(order: RestPlaceOrderRequest): PlaceOrCancelOrder? {
        return null
    }

    open fun cancelOrder(order: RestCancelOrderRequest): PlaceOrCancelOrder? {
        return null
    }

    fun <T> executeRequest(request: Request, clazz: Class<T>): T = try {
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
        asObject(respBody, clazz)
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

    /**
     * @return two timestamps, range for bar history
     * @param interval bars interval
     * @param limit max bars count
     */
    private fun getFrom(interval: BitmaxInterval, limit: Int): LongArray {
        val time = System.currentTimeMillis()
        return when (interval) {
            BitmaxInterval.ONE_MINUTE -> longArrayOf(time - 60000L * limit, time)
            BitmaxInterval.FIVE_MINUTES -> longArrayOf(time - 300000L * limit, time)
            BitmaxInterval.FIFTEEN_MINUTES -> longArrayOf(time - 900000L * limit, time)
            BitmaxInterval.HALF_HOURLY -> longArrayOf(time - 1800000L * limit, time)
            BitmaxInterval.HOURLY -> longArrayOf(time - 3600000L * limit, time)
            BitmaxInterval.TWO_HOURLY -> longArrayOf(time - 3600000L * 2 * limit, time)
            BitmaxInterval.FOUR_HOURLY -> longArrayOf(time - 3600000L * 4 * limit, time)
            BitmaxInterval.SIX_HOURLY -> longArrayOf(time - 3600000L * 6 * limit, time)
            BitmaxInterval.TWELVE_HOURLY -> longArrayOf(time - 3600000L * 12 * limit, time)
            BitmaxInterval.DAILY -> longArrayOf(time - 3600000L * 24 * limit, time)
            BitmaxInterval.WEEKLY -> longArrayOf(time - 3600000L * 24 * 7 * limit, time)
            BitmaxInterval.MONTHLY -> longArrayOf(time - 3600000L * 24 * 31 * limit, time)
        }
    }

}