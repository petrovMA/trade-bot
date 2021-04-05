package io.bitmax.api.rest.client

import bot.telegram.notificator.exchanges.clients.INTERVAL
import bot.telegram.notificator.exchanges.libs.bitmax.BitmaxCandlestick
import bot.telegram.notificator.libs.NotSupportedIntervalException
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
    val url = "https://ascendex.com/"
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
    fun getCandlestickBars(symbol: String, interval: INTERVAL, limit: Int): List<RestBarHist> {
        val between = getFrom(interval, limit)
        val params = "?symbol=$symbol&interval=${getInterval(interval)}&n=$limit"
        return try {
            executeRequest(
                requestBuilder
                    .url(url + api + pathBars + params)
                    .get()
                    .build(), RestBarHists::class.java
            ).data
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
    private fun getFrom(interval: INTERVAL, limit: Int): Pair<Long, Long> {
        val time = System.currentTimeMillis()
        return when (interval) {
            INTERVAL.ONE_MINUTE -> time - 60000L * limit to time
            INTERVAL.THREE_MINUTES -> time - 180000L * limit to time
            INTERVAL.FIVE_MINUTES -> time - 300000L * limit to time
            INTERVAL.FIFTEEN_MINUTES -> time - 900000L * limit to time
            INTERVAL.HALF_HOURLY -> time - 1800000L * limit to time
            INTERVAL.HOURLY -> time - 3600000L * limit to time
            INTERVAL.TWO_HOURLY -> time - 3600000L * 2 * limit to time
            INTERVAL.FOUR_HOURLY -> time - 3600000L * 4 * limit to time
            INTERVAL.SIX_HOURLY -> time - 3600000L * 6 * limit to time
            INTERVAL.EIGHT_HOURLY -> time - 3600000L * 8 * limit to time
            INTERVAL.TWELVE_HOURLY -> time - 3600000L * 12 * limit to time
            INTERVAL.DAILY -> time - 3600000L * 24 * limit to time
            INTERVAL.THREE_DAILY -> time - 3600000L * 24 * 3 * limit to time
            INTERVAL.WEEKLY -> time - 3600000L * 24 * 7 * limit to time
            INTERVAL.MONTHLY -> time - 3600000L * 24 * 31 * limit to time
        }
    }


    private fun getInterval(interval: INTERVAL): String = when (interval) {
        INTERVAL.ONE_MINUTE -> "1"
        INTERVAL.FIVE_MINUTES -> "5"
        INTERVAL.FIFTEEN_MINUTES -> "15"
        INTERVAL.HALF_HOURLY -> "30"
        INTERVAL.HOURLY -> "60"
        INTERVAL.TWO_HOURLY -> "120"
        INTERVAL.FOUR_HOURLY -> "240"
        INTERVAL.SIX_HOURLY -> "360"
        INTERVAL.TWELVE_HOURLY -> "720"
        INTERVAL.DAILY -> "1d"
        INTERVAL.WEEKLY -> "1w"
        INTERVAL.MONTHLY -> "1m"
        else -> throw NotSupportedIntervalException()
    }
}