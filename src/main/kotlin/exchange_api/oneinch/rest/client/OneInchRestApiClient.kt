package exchange_api.oneinch.rest.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import exchange_api.oneinch.rest.response.*
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * REST client for 1inch DEV API (api.1inch.dev).
 * Handles orderbook, balance, and price endpoints for BSC (chainId=56).
 */
class OneInchRestApiClient(
    private val apiKey: String,
    private val chainId: Int = 56
) {
    private val baseUrl = "https://api.1inch.dev"
    private val gson = Gson()
    private val log = KotlinLogging.logger {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): Pair<String, String> = "Authorization" to "Bearer $apiKey"

    /**
     * Submit a signed limit order to the orderbook.
     * POST /orderbook/v4.1/{chainId}
     */
    fun submitLimitOrder(request: SubmitOrderRequest): SubmitOrderResponse {
        val url = "$baseUrl/orderbook/v4.1/$chainId"
        val json = gson.toJson(request)

        log.debug { "Submitting limit order: $url" }
        log.debug { "Request body: $json" }

        val httpRequest = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
            .header(authHeader().first, authHeader().second)
            .build()

        return executeRequest(httpRequest)
    }

    /**
     * Get all orders created by a specific wallet address.
     * GET /orderbook/v4.0/{chainId}/address/{address}
     */
    fun getOrdersByCreator(
        walletAddress: String,
        statuses: List<Int> = listOf(1, 2)
    ): List<OneInchOrderResponse> {
        val statusParam = statuses.joinToString(",")
        val url = "$baseUrl/orderbook/v4.0/$chainId/address/$walletAddress?statuses=%5B$statusParam%5D"

        log.debug { "Getting orders by creator: $url" }

        val request = Request.Builder()
            .url(url)
            .get()
            .header(authHeader().first, authHeader().second)
            .build()

        return executeRequest(request)
    }

    /**
     * Get a specific order by its hash.
     * GET /orderbook/v4.1/{chainId}/order/{orderHash}
     */
    fun getOrderByHash(orderHash: String): OneInchOrderResponse {
        val url = "$baseUrl/orderbook/v4.1/$chainId/order/$orderHash"

        log.debug { "Getting order by hash: $url" }

        val request = Request.Builder()
            .url(url)
            .get()
            .header(authHeader().first, authHeader().second)
            .build()

        return executeRequest(request)
    }

    /**
     * Get token balances for a wallet.
     * GET /balance/v1.2/{chainId}/balances/{walletAddress}
     */
    fun getBalances(walletAddress: String): BalancesResponse {
        val url = "$baseUrl/balance/v1.2/$chainId/balances/$walletAddress"

        log.debug { "Getting balances: $url" }

        val request = Request.Builder()
            .url(url)
            .get()
            .header(authHeader().first, authHeader().second)
            .build()

        return executeRequest(request)
    }

    /**
     * Get spot prices for tokens.
     * GET /price/v1.1/{chainId}/{tokenAddresses}
     * @param tokenAddresses comma-separated list of token addresses
     */
    fun getTokenPrices(tokenAddresses: String): TokenPricesResponse {
        val url = "$baseUrl/price/v1.1/$chainId/$tokenAddresses"

        log.debug { "Getting token prices: $url" }

        val request = Request.Builder()
            .url(url)
            .get()
            .header(authHeader().first, authHeader().second)
            .build()

        return executeRequest(request)
    }

    /**
     * Get spot price for a single token.
     */
    fun getTokenPrice(tokenAddress: String): String? {
        val prices = getTokenPrices(tokenAddress)
        return prices[tokenAddress.lowercase()] ?: prices[tokenAddress]
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        val startTime = System.currentTimeMillis()

        try {
            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val body = response.body?.string()

            log.debug {
                buildString {
                    appendLine("=== 1inch REST API Response ===")
                    appendLine("URL: ${request.url}")
                    appendLine("Status: ${response.code}")
                    appendLine("Duration: ${duration}ms")
                    appendLine("Response Body: $body")
                    appendLine("===============================")
                }
            }

            if (!response.isSuccessful) {
                throw OneInchApiException(
                    "1inch API error: ${response.code} ${response.message} - $body",
                    response.code
                )
            }

            if (body.isNullOrBlank()) {
                throw OneInchApiException("1inch API returned empty response", response.code)
            }

            val type = object : TypeToken<T>() {}.type
            return gson.fromJson(body, type)
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            log.error("1inch API request failed: ${request.method} ${request.url} (${duration}ms)", e)
            throw e
        }
    }
}

class OneInchApiException(message: String, val statusCode: Int) : RuntimeException(message)
