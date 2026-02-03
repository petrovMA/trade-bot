package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamOneInchPolling
import com.typesafe.config.Config
import exchange_api.oneinch.order.LimitOrderBuilder
import exchange_api.oneinch.rest.client.OneInchRestApiClient
import exchange_api.oneinch.token.BscTokenRegistry
import exchange_api.oneinch.token.TokenApprovalHelper
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Client implementation for 1inch DEX on BSC (BNB Chain).
 * Uses the 1inch Limit Order Protocol v4 for orderbook-based limit orders.
 *
 * Key differences from CEX clients:
 * - Orders are signed off-chain via EIP-712 and settled on-chain
 * - Uses token contract addresses instead of pair symbols
 * - No WebSocket streams — polling-based price/order updates
 * - Cancel = let orders expire (on-chain cancel costs gas)
 * - Order ID = EIP-712 order hash
 */
class ClientOneInch(
    private val apiKey: String,
    private val privateKey: String,
    private val config: Config
) : Client {

    private val log = KotlinLogging.logger {}

    private val chainId = if (config.hasPath("chain_id")) config.getInt("chain_id") else 56
    private val orderExpirySeconds =
        if (config.hasPath("order_expiry_seconds")) config.getLong("order_expiry_seconds") else 3600L
    private val pollIntervalMs =
        if (config.hasPath("poll_interval")) config.getDuration("poll_interval").toMillis() else 5000L
    private val bscRpcUrl =
        if (config.hasPath("bsc_rpc_url")) config.getString("bsc_rpc_url") else "https://bsc-dataseed.binance.org/"
    private val limitOrderProtocol =
        if (config.hasPath("limit_order_protocol")) config.getString("limit_order_protocol")
        else "0x111111125421cA6dc452d289314280a0f8842A65"

    val tokenRegistry = BscTokenRegistry(config)
    private val restClient = OneInchRestApiClient(apiKey, chainId)
    private val orderBuilder = LimitOrderBuilder(privateKey, tokenRegistry, chainId, limitOrderProtocol)
    private val approvalHelper = TokenApprovalHelper(privateKey, bscRpcUrl, limitOrderProtocol)

    val walletAddress: String = orderBuilder.walletAddress

    // Track approved tokens to avoid redundant checks
    private val approvedTokens = ConcurrentHashMap.newKeySet<String>()

    // Active polling stream (one per pair)
    private var activeStream: StreamOneInchPolling? = null

    override fun getAllPairs(): List<TradePair> {
        log.warn("getAllPairs() not supported for 1inch DEX - returns empty list")
        return emptyList()
    }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {
        log.debug("getCandlestickBars called for 1inch - using spot price as synthetic candlestick")

        return try {
            val baseAddress = tokenRegistry.getAddress(pair.first)
            val priceStr = restClient.getTokenPrice(baseAddress)
            val currentTime = System.currentTimeMillis()
            val price = priceStr?.toBigDecimalOrNull() ?: BigDecimal.ONE

            listOf(
                Candlestick(
                    openTime = currentTime - 60000,
                    closeTime = currentTime,
                    open = price,
                    high = price,
                    low = price,
                    close = price,
                    volume = BigDecimal.ZERO
                )
            )
        } catch (e: Exception) {
            log.error("Failed to get price data for $pair", e)
            val currentTime = System.currentTimeMillis()
            listOf(
                Candlestick(
                    openTime = currentTime - 60000,
                    closeTime = currentTime,
                    open = BigDecimal.ONE,
                    high = BigDecimal.ONE,
                    low = BigDecimal.ONE,
                    close = BigDecimal.ONE,
                    volume = BigDecimal.ZERO
                )
            )
        }
    }

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> {
        log.warn("getCandlestickBars with time range called - delegating to simple version")
        return getCandlestickBars(pair, interval, countCandles)
    }

    override fun getOpenOrders(pair: TradePair): List<Order> {
        val orders = restClient.getOrdersByCreator(walletAddress, statuses = listOf(1, 2))
        val baseAddress = tokenRegistry.getAddress(pair.first).lowercase()
        val quoteAddress = tokenRegistry.getAddress(pair.second).lowercase()

        return orders.filter { orderResp ->
            val makerAsset = orderResp.data?.makerAsset?.lowercase()
            val takerAsset = orderResp.data?.takerAsset?.lowercase()
            (makerAsset == baseAddress && takerAsset == quoteAddress) ||
                    (makerAsset == quoteAddress && takerAsset == baseAddress)
        }.mapNotNull { mapOrderResponse(it, pair) }
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =
        pairs.associateWith { getOpenOrders(it) }

    override fun getBalances(): Map<String, List<Balance>> {
        val balancesWei = restClient.getBalances(walletAddress)
        val balances = mutableListOf<Balance>()

        for ((address, weiStr) in balancesWei) {
            val symbol = tokenRegistry.getSymbol(address) ?: continue
            val wei = try {
                BigInteger(weiStr)
            } catch (_: Exception) {
                continue
            }
            if (wei <= BigInteger.ZERO) continue

            val amount = tokenRegistry.fromWei(wei, symbol)
            balances.add(
                Balance(
                    asset = symbol,
                    total = amount,
                    free = amount, // DEX: all balance is "free" (no lock concept)
                    locked = BigDecimal.ZERO
                )
            )
        }

        return mapOf("spot" to balances)
    }

    override fun getBalance(coin: String): Balance? {
        val resolvedSymbol = tokenRegistry.resolveSymbol(coin)
        val address = try {
            tokenRegistry.getAddress(coin)
        } catch (_: Exception) {
            return null
        }

        val balancesWei = restClient.getBalances(walletAddress)
        val weiStr = balancesWei[address] ?: balancesWei[address.lowercase()] ?: return null

        val wei = try {
            BigInteger(weiStr)
        } catch (_: Exception) {
            return null
        }

        val amount = tokenRegistry.fromWei(wei, coin)
        return Balance(
            asset = resolvedSymbol,
            total = amount,
            free = amount,
            locked = BigDecimal.ZERO
        )
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook {
        // Synthetic order book from current price
        return try {
            val baseAddress = tokenRegistry.getAddress(pair.first)
            val priceStr = restClient.getTokenPrice(baseAddress)
            val price = priceStr?.toBigDecimalOrNull() ?: BigDecimal.ONE

            OrderBook(
                bids = listOf(Offer(price, BigDecimal.ONE)),
                asks = listOf(Offer(price, BigDecimal.ONE))
            )
        } catch (e: Exception) {
            log.error("Failed to get order book for $pair", e)
            OrderBook(bids = emptyList(), asks = emptyList())
        }
    }

    override fun getAssetBalance(asset: String): Map<String, Balance?> {
        val balance = getBalance(asset)
        return mapOf("spot" to balance)
    }

    override fun getOrder(pair: TradePair, orderId: String): Order? {
        return try {
            val orderResp = restClient.getOrderByHash(orderId)
            mapOrderResponse(orderResp, pair)
        } catch (e: Exception) {
            log.warn("Failed to get order $orderId: ${e.message}")
            null
        }
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        val pair = order.pair
        val priceBd = BigDecimal(price)
        val qtyBd = BigDecimal(qty)

        // Determine maker/taker assets based on side
        // BUY BTC/USDT: maker gives USDT, wants BTC -> makerAsset=USDT, takerAsset=BTC
        // SELL BTC/USDT: maker gives BTC, wants USDT -> makerAsset=BTC, takerAsset=USDT
        val (makerSymbol, takerSymbol, makingAmount, takingAmount) = when (order.side) {
            SIDE.BUY -> {
                val quoteAmount = qtyBd.multiply(priceBd)
                OrderParams(pair.second, pair.first, quoteAmount, qtyBd)
            }
            SIDE.SELL -> {
                val quoteAmount = qtyBd.multiply(priceBd)
                OrderParams(pair.first, pair.second, qtyBd, quoteAmount)
            }
            else -> throw IllegalArgumentException("Unsupported order side: ${order.side}")
        }

        // Ensure token approval before placing order
        ensureTokenApproval(makerSymbol)

        // Build and sign the limit order
        val signedOrder = orderBuilder.buildAndSign(
            makerAssetSymbol = makerSymbol,
            takerAssetSymbol = takerSymbol,
            makingAmount = makingAmount,
            takingAmount = takingAmount,
            expirySeconds = orderExpirySeconds
        )

        // Submit to 1inch orderbook API
        val submitRequest = orderBuilder.toSubmitRequest(signedOrder)
        val response = restClient.submitLimitOrder(submitRequest)

        val orderId = signedOrder.orderHash

        log.info("1inch order submitted: ${order.side} ${pair} qty=$qty price=$price hash=$orderId")

        // Track in active stream
        activeStream?.trackOrder(orderId)

        return Order(
            orderId = orderId,
            pair = pair,
            price = priceBd,
            origQty = qtyBd,
            executedQty = BigDecimal.ZERO,
            side = order.side,
            type = TYPE.LIMIT,
            status = STATUS.NEW
        )
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        // On-chain cancellation costs gas. Orders use expiry and expire naturally.
        // Return true to prevent Algorithm retry loops.
        log.info("1inch cancelOrder: order $orderId will expire naturally (on-chain cancel costs gas)")
        return true
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream {
        val stream = StreamOneInchPolling(
            pair = pair,
            restClient = restClient,
            tokenRegistry = tokenRegistry,
            walletAddress = walletAddress,
            queue = queue,
            pollIntervalMs = pollIntervalMs
        )
        activeStream = stream
        return stream
    }

    override fun close() {
        activeStream?.stopPolling()
        approvalHelper.close()
    }

    private fun ensureTokenApproval(symbol: String) {
        val address = tokenRegistry.getAddress(symbol)
        if (approvedTokens.contains(address)) return

        try {
            if (approvalHelper.ensureApproval(address)) {
                approvedTokens.add(address)
                log.info("Token $symbol ($address) approved for 1inch protocol")
            } else {
                log.warn("Token approval failed for $symbol ($address) - order may fail on-chain")
            }
        } catch (e: Exception) {
            log.warn("Token approval check failed for $symbol: ${e.message}")
        }
    }

    private fun mapOrderResponse(orderResp: exchange_api.oneinch.rest.response.OneInchOrderResponse, pair: TradePair): Order? {
        val data = orderResp.data ?: return null
        val makerAsset = data.makerAsset?.lowercase() ?: return null
        val quoteAddress = tokenRegistry.getAddress(pair.second).lowercase()

        // Determine side
        val isBuy = makerAsset == quoteAddress
        val side = if (isBuy) SIDE.BUY else SIDE.SELL

        val makingAmount = data.makingAmount ?: return null
        val takingAmount = data.takingAmount ?: return null

        val (baseAmount, quoteAmount) = if (isBuy) {
            tokenRegistry.fromWei(takingAmount, pair.first) to tokenRegistry.fromWei(makingAmount, pair.second)
        } else {
            tokenRegistry.fromWei(makingAmount, pair.first) to tokenRegistry.fromWei(takingAmount, pair.second)
        }

        val price = if (baseAmount > BigDecimal.ZERO) {
            quoteAmount.divide(baseAmount, 8, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        // Determine fill status from remainingMakerAmount
        val status = when {
            orderResp.isFilled() -> STATUS.FILLED
            orderResp.isPartiallyFilled() -> STATUS.PARTIALLY_FILLED
            orderResp.isActive() -> STATUS.NEW
            else -> STATUS.CANCELED
        }

        val executedQty = if (status == STATUS.FILLED) {
            baseAmount
        } else if (orderResp.isPartiallyFilled()) {
            val remainingWei = BigInteger(orderResp.remainingMakerAmount ?: "0")
            val originalWei = BigInteger(makingAmount)
            val filledWei = originalWei.subtract(remainingWei)
            if (isBuy) {
                // For BUY: maker gives quote, remaining is in quote units
                // Approximate base amount filled proportionally
                val fillRatio = BigDecimal(filledWei).divide(BigDecimal(originalWei), 8, RoundingMode.HALF_UP)
                baseAmount.multiply(fillRatio)
            } else {
                tokenRegistry.fromWei(filledWei, pair.first)
            }
        } else {
            BigDecimal.ZERO
        }

        return Order(
            orderId = orderResp.orderHash ?: return null,
            pair = pair,
            price = price,
            origQty = baseAmount,
            executedQty = executedQty,
            side = side,
            type = TYPE.LIMIT,
            status = status
        )
    }

    private data class OrderParams(
        val makerSymbol: String,
        val takerSymbol: String,
        val makingAmount: BigDecimal,
        val takingAmount: BigDecimal
    )

    override fun toString(): String = "ONEINCH"
}
