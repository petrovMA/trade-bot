package exchange_api.gate.rest.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import exchange_api.gate.rest.response.GateOrderResponse
import mu.KotlinLogging
import org.knowm.xchange.Exchange
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.currency.CurrencyPair
import org.knowm.xchange.dto.Order
import org.knowm.xchange.dto.account.AccountInfo
import org.knowm.xchange.dto.account.Balance
import org.knowm.xchange.dto.trade.LimitOrder
import org.knowm.xchange.dto.trade.OpenOrders
import org.knowm.xchange.gateio.GateioExchange
import org.knowm.xchange.gateio.service.GateioAccountService
import org.knowm.xchange.gateio.service.GateioTradeService
import org.knowm.xchange.gateio.service.GateioTradeServiceRaw
import org.knowm.xchange.service.trade.params.DefaultCancelOrderByInstrumentAndIdParams
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GateRestApiClient(private val apikey: String, private val secret: String) {

    private val log = KotlinLogging.logger {}
    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    companion object {
        private const val BASE_URL = "https://api.gateio.ws"
    }

    private val exchange: Exchange = ExchangeFactory.INSTANCE.createExchange(
        GateioExchange::class.java, apikey, secret
    )

    private val accountService: GateioAccountService = exchange.accountService as GateioAccountService
    private val tradeService: GateioTradeService = exchange.tradeService as GateioTradeService
    private val tradeServiceRaw: GateioTradeServiceRaw = exchange.tradeService as GateioTradeServiceRaw
    
    fun getBalance(): Map<String, Balance> {
        return try {
            val accountInfo: AccountInfo = accountService.accountInfo
            log.info("Retrieved account info for Gate.io")
            
            // Get the trading wallet (spot trading)
            val tradingWallet = accountInfo.getWallet("trading") 
                ?: accountInfo.wallets.values.firstOrNull()
            
            // Convert Currency keys to String keys
            tradingWallet?.balances?.mapKeys { it.key.currencyCode } ?: emptyMap()
        } catch (e: Exception) {
            log.error("Failed to get balance from Gate.io", e)
            emptyMap()
        }
    }
    
    fun newOrder(
        symbol: String,
        category: String = "spot",
        side: String,
        orderType: String,
        qty: String,
        price: String? = null,
        timeInForce: String = "GTC"
    ): GateOrderResponse {
        return try {
            log.info("Placing new order: $symbol $side $qty @ $price")
            
            val currencyPair = parseCurrencyPair(symbol)
            val orderSide = if (side.equals("Buy", ignoreCase = true)) Order.OrderType.BID else Order.OrderType.ASK
            val amount = BigDecimal(qty)
            val limitPrice = price?.let { BigDecimal(it) }
            
            // Generate Gate.io compliant client order ID (must start with "t-")
            val clientOrderId = "t-${System.currentTimeMillis()}-${UUID.randomUUID().toString().substring(0, 8)}"
            
            // Create LimitOrder with proper client order ID for Gate.io
            val limitOrder = LimitOrder.Builder(orderSide, currencyPair)
                .originalAmount(amount)
                .limitPrice(limitPrice)
                .id(clientOrderId) // This should be the client order ID with "t-" prefix
                .timestamp(Date())
                .userReference(clientOrderId) // Gate.io uses this for the "text" field
                .build()
            
            GateApiThrottler.awaitSlot("placeLimitOrder($symbol)")
            val orderId = tradeService.placeLimitOrder(limitOrder)
            log.info("Order placed successfully: $orderId")
            
            GateOrderResponse(
                orderId = orderId ?: clientOrderId, // Use returned order ID or fallback to client order ID
                symbol = symbol,
                side = side,
                orderType = orderType,
                qty = amount,
                price = limitPrice ?: BigDecimal.ZERO,
                status = "NEW"
            )
        } catch (e: Exception) {
            log.error("Failed to place order", e)
            throw e
        }
    }
    
    fun getOpenOrders(
        category: String = "spot",
        symbol: String? = null,
        orderId: String? = null
    ): List<GateOrderResponse> {
        return try {
            log.info("Getting open orders for symbol: $symbol, orderId: $orderId")
            
            // Get all open orders and filter as needed
            val openOrders: OpenOrders = tradeService.openOrders
            log.info("Retrieved ${openOrders.openOrders.size} open orders from exchange")
            
            val orders = openOrders.openOrders.map { order ->
                val limitOrder = order as LimitOrder
                log.info("Processing order - ID: ${limitOrder.id}, UserRef: ${limitOrder.userReference}, Status: ${limitOrder.status}")
                
                GateOrderResponse(
                    orderId = limitOrder.id,
                    symbol = "${limitOrder.currencyPair.base.currencyCode}_${limitOrder.currencyPair.counter.currencyCode}",
                    side = if (limitOrder.type == Order.OrderType.BID) "Buy" else "Sell",
                    orderType = "Limit",
                    qty = limitOrder.originalAmount,
                    price = limitOrder.limitPrice,
                    status = mapOrderStatus(limitOrder.status),
                    executedQty = limitOrder.cumulativeAmount
                )
            }
            
            // Filter by symbol and/or orderId if specified
            var filteredOrders = orders
            if (symbol != null) {
                filteredOrders = filteredOrders.filter { it.symbol.equals(symbol, ignoreCase = true) }
            }
            if (orderId != null) {
                // Check both order ID and user reference (client order ID) for matches
                filteredOrders = filteredOrders.filter { order ->
                    val limitOrder = openOrders.openOrders.find { it.id == order.orderId } as? LimitOrder
                    order.orderId == orderId || limitOrder?.userReference == orderId
                }
            }
            
            log.info("Returning ${filteredOrders.size} filtered orders")
            filteredOrders
            
        } catch (e: Exception) {
            log.error("Failed to get open orders", e)
            emptyList()
        }
    }
    
    fun orderCancel(
        category: String = "spot",
        symbol: String,
        orderId: String
    ): Boolean {
        return try {
            log.info("Canceling order: $orderId for symbol: $symbol")
            
            val currencyPair = parseCurrencyPair(symbol)
            val cancelParams = DefaultCancelOrderByInstrumentAndIdParams(currencyPair, orderId)
            GateApiThrottler.awaitSlot("cancelOrder($symbol,$orderId)")
            val cancelled = tradeService.cancelOrder(cancelParams)
            
            log.info("Order cancellation result: $cancelled")
            true // If no exception thrown, consider it successful
        } catch (e: Exception) {
            log.error("Failed to cancel order: $orderId", e)
            false
        }
    }
    
    private fun parseCurrencyPair(symbol: String): CurrencyPair {
        // Handle symbols like "GT_USDT" or "GTUSDT"
        return if (symbol.contains("_")) {
            val parts = symbol.split("_")
            CurrencyPair(parts[0], parts[1])
        } else {
            // For symbols without underscore, try to parse common patterns
            when {
                symbol.endsWith("USDT") -> CurrencyPair(symbol.removeSuffix("USDT"), "USDT")
                symbol.endsWith("BTC") -> CurrencyPair(symbol.removeSuffix("BTC"), "BTC")
                symbol.endsWith("ETH") -> CurrencyPair(symbol.removeSuffix("ETH"), "ETH")
                else -> throw IllegalArgumentException("Cannot parse currency pair from: $symbol")
            }
        }
    }
    
    private fun mapOrderStatus(status: Order.OrderStatus?): String {
        return when (status) {
            Order.OrderStatus.NEW, Order.OrderStatus.PENDING_NEW, Order.OrderStatus.OPEN -> "NEW"
            Order.OrderStatus.FILLED -> "FILLED"
            Order.OrderStatus.PARTIALLY_FILLED -> "PARTIALLY_FILLED"
            Order.OrderStatus.CANCELED, Order.OrderStatus.REJECTED,
            Order.OrderStatus.EXPIRED, Order.OrderStatus.STOPPED -> "CANCELLED"
            else -> "UNKNOWN"
        }
    }

    /**
     * Get order by ID using Gate.io REST API v4 directly.
     * This bypasses XChange library issues with getOrder method.
     *
     * @param symbol Trading pair (e.g., "REACT_USDT")
     * @param orderId Order ID to query
     * @return GateOrderResponse with order details
     */
    fun getOrder(symbol: String, orderId: String): GateOrderResponse {
        log.info("Getting order: $orderId for symbol: $symbol")

        val path = "/api/v4/spot/orders/$orderId"
        val queryString = "currency_pair=${symbol.replace("/", "_")}"
        val fullPath = "$path?$queryString"

        GateApiThrottler.awaitSlot("getOrder($symbol,$orderId)")

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = generateSignature("GET", path, queryString, "", timestamp)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL$fullPath"))
            .header("KEY", apikey)
            .header("SIGN", signature)
            .header("Timestamp", timestamp)
            .header("Content-Type", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            log.error("Failed to get order. Status: ${response.statusCode()}, Body: ${response.body()}")
            throw RuntimeException("Failed to get order: ${response.body()}")
        }

        val orderData: Map<String, Any> = objectMapper.readValue(
            response.body(),
            object : TypeReference<Map<String, Any>>() {}
        )
        log.info("Order response: $orderData")

        return GateOrderResponse(
            orderId = orderData["id"]?.toString() ?: orderId,
            symbol = orderData["currency_pair"]?.toString() ?: symbol,
            side = if (orderData["side"]?.toString() == "buy") "Buy" else "Sell",
            orderType = orderData["type"]?.toString() ?: "limit",
            qty = BigDecimal(orderData["amount"]?.toString() ?: "0"),
            price = BigDecimal(orderData["price"]?.toString() ?: "0"),
            status = mapGateOrderStatus(orderData["status"]?.toString()),
            executedQty = BigDecimal(orderData["filled_total"]?.toString() ?: "0")
        )
    }

    private fun mapGateOrderStatus(status: String?): String {
        return when (status?.lowercase()) {
            "open" -> "NEW"
            "closed" -> "FILLED"
            "cancelled" -> "CANCELLED"
            else -> status?.uppercase() ?: "UNKNOWN"
        }
    }

    private fun generateSignature(
        method: String,
        path: String,
        queryString: String,
        body: String,
        timestamp: String
    ): String {
        // Hash the body (empty string for GET requests)
        val bodyHash = sha512Hash(body)

        // Create the signing string: method\npath\nqueryString\nbodyHash\ntimestamp
        val signingString = "$method\n$path\n$queryString\n$bodyHash\n$timestamp"

        // Sign with HMAC-SHA512
        return hmacSha512(secret, signingString)
    }

    private fun sha512Hash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha512(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA512")
        mac.init(secretKey)
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get trade history (executed orders) from Gate.io.
     *
     * @param symbol Trading pair (e.g., "REACT_USDT")
     * @param from Start timestamp in seconds (optional)
     * @param to End timestamp in seconds (optional)
     * @param limit Max number of records (default 100, max 1000)
     * @return List of executed trades
     */
    fun getMyTrades(
        symbol: String,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 1000
    ): List<GateTradeResponse> {
        log.info("Getting trade history for symbol: $symbol, from: $from, to: $to, limit: $limit")

        val path = "/api/v4/spot/my_trades"
        val queryParams = mutableListOf<String>()
        queryParams.add("currency_pair=${symbol.replace("/", "_")}")
        queryParams.add("limit=$limit")
        from?.let { queryParams.add("from=$it") }
        to?.let { queryParams.add("to=$it") }

        val queryString = queryParams.joinToString("&")
        val fullPath = "$path?$queryString"

        GateApiThrottler.awaitSlot("getMyTrades($symbol)")

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = generateSignature("GET", path, queryString, "", timestamp)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$BASE_URL$fullPath"))
            .header("KEY", apikey)
            .header("SIGN", signature)
            .header("Timestamp", timestamp)
            .header("Content-Type", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            log.error("Failed to get trades. Status: ${response.statusCode()}, Body: ${response.body()}")
            throw RuntimeException("Failed to get trades: ${response.body()}")
        }

        val tradesData: List<Map<String, Any>> = objectMapper.readValue(
            response.body(),
            object : TypeReference<List<Map<String, Any>>>() {}
        )
        log.info("Retrieved ${tradesData.size} trades")

        return tradesData.map { trade ->
            GateTradeResponse(
                tradeId = trade["id"]?.toString() ?: "",
                orderId = trade["order_id"]?.toString() ?: "",
                symbol = trade["currency_pair"]?.toString() ?: symbol,
                side = if (trade["side"]?.toString() == "buy") "Buy" else "Sell",
                amount = BigDecimal(trade["amount"]?.toString() ?: "0"),
                price = BigDecimal(trade["price"]?.toString() ?: "0"),
                fee = BigDecimal(trade["fee"]?.toString() ?: "0"),
                feeCurrency = trade["fee_currency"]?.toString() ?: "",
                timestamp = trade["create_time_ms"]?.toString()?.toLongOrNull()
                    ?: (trade["create_time"]?.toString()?.toLongOrNull()?.times(1000) ?: 0L),
                role = trade["role"]?.toString() ?: "" // maker or taker
            )
        }
    }
}

/**
 * Response for a single trade from Gate.io
 */
data class GateTradeResponse(
    val tradeId: String,
    val orderId: String,
    val symbol: String,
    val side: String,
    val amount: BigDecimal,
    val price: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: String,
    val timestamp: Long,
    val role: String
) {
    val total: BigDecimal get() = amount * price
}