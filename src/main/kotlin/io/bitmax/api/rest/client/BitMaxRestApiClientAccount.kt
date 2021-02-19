package io.bitmax.api.rest.client

import io.bitmax.api.Authorization
import io.bitmax.api.Mapper.asString
import io.bitmax.api.rest.messages.requests.RestCancelOrderRequest
import io.bitmax.api.rest.messages.requests.RestPlaceOrderRequest
import io.bitmax.api.rest.messages.responses.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * BitMaxRestApiClientAccount private restClient for authorized users.
 */
class BitMaxRestApiClientAccount(apiKey: String, secret: String) : BitMaxRestApiClient() {
    private val authClient: Authorization = Authorization(apiKey, secret)
    val accountGroup: Int

    init {
        accountGroup = userInfo.data.accountGroup
    }

    /**
     * @return 'UserInfo' object that contains 'userGroup' field
     * @throws RuntimeException throws if something wrong (e.g. not correct response)
     */
    override val userInfo: RestUserInfo
        get() {
            val headers = authClient.getHeaderMap(pathInfo, System.currentTimeMillis())
            val builder = requestBuilder
                    .url(url + api + pathInfo)
                    .get()
            for ((key, value) in headers) {
                builder.header(key, value)
            }
            return executeRequest(builder.build(), RestUserInfo::class.java)
        }

    /**
     * @return list of balances
     * @throws RuntimeException throws if something wrong (e.g. not correct response)
     */
    override val balance: RestBalances
        get() {
            val headers = authClient.getHeaderMap(balanceUrl, System.currentTimeMillis())
            val builder = requestBuilder
                    .url("$url$accountGroup/$api$cash$balanceUrl")
                    .get()
            for ((key, value) in headers) {
                builder.header(key, value)
            }
            return executeRequest(builder.build(), RestBalances::class.java)
        }

    /**
     * @return list of open orders
     * @throws RuntimeException throws if something wrong (e.g. not correct response)
     */
    override val openOrders: RestOpenOrdersList
        get() {
            val headers = authClient.getHeaderMap(pathOrders, System.currentTimeMillis())
            val builder = requestBuilder
                    .url("$url$accountGroup/$api$cash$pathOrders")
                    .get()
            for ((key, value) in headers) {
                builder.header(key, value)
            }
            return executeRequest(builder.build(), RestOpenOrdersList::class.java)
        }

    /**
     * @return detailed info about specific order
     * @throws RuntimeException throws if something wrong (e.g. not correct response)
     */
    override fun getOrder(orderId: String): RestOrderDetails {
        val headers = authClient.getHeaderMap(pathOrderStatus, System.currentTimeMillis())
        val builder = requestBuilder
                .url("$url$accountGroup/$api$cash$pathOrderStatus?orderId=$orderId")
                .get()
        for ((key, value) in headers) {
            builder.header(key, value)
        }
        return executeRequest(builder.build(), RestOrderDetails::class.java)
    }

    /**
     * @param order is object which contains information about order
     * @return Response - object which contains information about result place order request
     * @throws RuntimeException throws if something wrong (order was not published)
     */
    override fun placeOrder(order: RestPlaceOrderRequest): PlaceOrCancelOrder {
        val timestamp = System.currentTimeMillis()
        val headers = authClient.getHeaderMap(pathOrder, timestamp)
        val builder = requestBuilder
            .url("$url$accountGroup/$api$cash$pathOrder")
            .post(asString(order).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
        headers.forEach { builder.header(it.key, it.value) }
        return try {
            executeRequest(builder.build(), PlaceOrCancelOrder::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    /**
     * @param order is object which contains information about order
     * @return Response - object which contains information about result cancel order request
     * @throws RuntimeException throws if something wrong (order was not cancelled)
     */
    override fun cancelOrder(order: RestCancelOrderRequest): PlaceOrCancelOrder? {
        val timestamp = System.currentTimeMillis()
        val headers = authClient.getHeaderMap(pathOrder, timestamp)
        val builder = requestBuilder
                .url("$url$accountGroup/$api$cash$pathOrder")
                .delete(asString(order).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
        headers.forEach { builder.header(it.key, it.value) }
        return try {
            executeRequest(builder.build(), PlaceOrCancelOrder::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }
}