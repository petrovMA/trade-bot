package io.bitmax.api.rest.client

import bot.trade.exchanges.clients.INTERVAL
import bot.trade.libs.NotSupportedIntervalException
import utils.mapper.Mapper.asObject
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
    val log = KotlinLogging.logger {}

    /**
     * @return history of bars
     * @param symbol trade pair
     * @param interval bars interval
     * @param limit max bars count
     */
    fun getCandlestickBars(symbol: String, interval: INTERVAL, limit: Int): List<RestBarHist> {
        val params = "?symbol=$symbol&interval=${getInterval(interval)}&n=$limit"
        return try {
            executeRequest<RestBarHists>(
                requestBuilder
                    .url(url + api + pathBars + params)
                    .get()
                    .build()
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
        return executeRequest<OrderBook>(builder.build())
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
            asObject<Array<RestProduct>>(result.body!!.string())
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    val allAssets: Array<Asset>
        get() = try {
            executeRequest<Array<Asset>>(
                requestBuilder
                    .url(url + api + assets)
                    .get()
                    .build()
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

    inline fun <reified T> executeRequest(request: Request): T = try {
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
        asObject<T>(respBody)
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

    /**
     * @return two timestamps, range for bar history
     * @param interval bars interval
     */
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