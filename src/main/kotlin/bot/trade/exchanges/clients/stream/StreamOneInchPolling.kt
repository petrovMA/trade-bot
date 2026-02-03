package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import exchange_api.oneinch.rest.client.OneInchRestApiClient
import exchange_api.oneinch.rest.response.OneInchOrderResponse
import exchange_api.oneinch.token.BscTokenRegistry
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Polling-based stream for 1inch DEX.
 * No WebSocket available — polls spot price API for Trade events
 * and order status API for order fill detection.
 */
class StreamOneInchPolling(
    private val pair: TradePair,
    private val restClient: OneInchRestApiClient,
    private val tokenRegistry: BscTokenRegistry,
    private val walletAddress: String,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val pollIntervalMs: Long = 5000
) : Stream() {

    private val log = KotlinLogging.logger {}

    // Track known order states to detect transitions
    private val knownOrderStates = ConcurrentHashMap<String, OrderState>()

    private data class OrderState(
        val orderHash: String,
        val remainingMakerAmount: String,
        val isActive: Boolean,
        val isFilled: Boolean
    )

    @Volatile
    private var running = true

    override fun run() {
        log.info("StreamOneInchPolling started for $pair (poll every ${pollIntervalMs}ms)")

        while (running && !isInterrupted) {
            try {
                pollPrices()
                pollOrderStatuses()
                Thread.sleep(pollIntervalMs)
            } catch (e: InterruptedException) {
                log.info("StreamOneInchPolling interrupted for $pair")
                running = false
            } catch (e: Exception) {
                log.warn("StreamOneInchPolling error for $pair: ${e.message}")
                try {
                    Thread.sleep(pollIntervalMs * 2)
                } catch (_: InterruptedException) {
                    running = false
                }
            }
        }

        log.info("StreamOneInchPolling stopped for $pair")
    }

    private fun pollPrices() {
        try {
            val baseAddress = tokenRegistry.getAddress(pair.first)
            val price = restClient.getTokenPrice(baseAddress)

            if (price != null) {
                val priceBd = BigDecimal(price)
                val trade = Trade(
                    price = priceBd,
                    qty = BigDecimal.ZERO,
                    time = System.currentTimeMillis()
                )
                queue.put(trade)
            }
        } catch (e: Exception) {
            log.debug("Failed to poll price for $pair: ${e.message}")
        }
    }

    private fun pollOrderStatuses() {
        try {
            val orders = restClient.getOrdersByCreator(walletAddress, statuses = listOf(1, 2, 3))

            val baseAddress = tokenRegistry.getAddress(pair.first)
            val quoteAddress = tokenRegistry.getAddress(pair.second)

            for (orderResp in orders) {
                val hash = orderResp.orderHash ?: continue
                val data = orderResp.data ?: continue

                // Filter to orders matching this pair
                val makerAsset = data.makerAsset?.lowercase()
                val takerAsset = data.takerAsset?.lowercase()
                if (!isPairMatch(makerAsset, takerAsset, baseAddress, quoteAddress)) continue

                val newState = OrderState(
                    orderHash = hash,
                    remainingMakerAmount = orderResp.remainingMakerAmount ?: "0",
                    isActive = orderResp.isActive(),
                    isFilled = orderResp.isFilled()
                )

                val oldState = knownOrderStates[hash]
                knownOrderStates[hash] = newState

                // Detect transition to filled
                if (newState.isFilled && (oldState == null || !oldState.isFilled)) {
                    log.info("Order filled detected: $hash")
                    val order = mapToOrder(orderResp, baseAddress, quoteAddress)
                    if (order != null) {
                        queue.put(order)
                    }
                }
                // Detect partial fill
                else if (orderResp.isPartiallyFilled() && oldState != null &&
                    oldState.remainingMakerAmount != newState.remainingMakerAmount
                ) {
                    log.info("Order partially filled: $hash (remaining: ${newState.remainingMakerAmount})")
                }
            }
        } catch (e: Exception) {
            log.debug("Failed to poll order statuses for $pair: ${e.message}")
        }
    }

    private fun isPairMatch(
        makerAsset: String?,
        takerAsset: String?,
        baseAddress: String,
        quoteAddress: String
    ): Boolean {
        val base = baseAddress.lowercase()
        val quote = quoteAddress.lowercase()
        return (makerAsset == base && takerAsset == quote) ||
                (makerAsset == quote && takerAsset == base)
    }

    private fun mapToOrder(
        orderResp: OneInchOrderResponse,
        baseAddress: String,
        quoteAddress: String
    ): Order? {
        val data = orderResp.data ?: return null
        val makerAsset = data.makerAsset?.lowercase() ?: return null

        // Determine side: if maker is giving quote token (USDT), it's a BUY
        val isBuy = makerAsset == quoteAddress.lowercase()
        val side = if (isBuy) SIDE.BUY else SIDE.SELL

        val makingAmount = data.makingAmount ?: return null
        val takingAmount = data.takingAmount ?: return null

        val (baseAmount, quoteAmount) = if (isBuy) {
            tokenRegistry.fromWei(takingAmount, pair.first) to tokenRegistry.fromWei(makingAmount, pair.second)
        } else {
            tokenRegistry.fromWei(makingAmount, pair.first) to tokenRegistry.fromWei(takingAmount, pair.second)
        }

        val price = if (baseAmount > BigDecimal.ZERO) {
            quoteAmount.divide(baseAmount, 8, java.math.RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val status = when {
            orderResp.isFilled() -> STATUS.FILLED
            orderResp.isPartiallyFilled() -> STATUS.PARTIALLY_FILLED
            orderResp.isActive() -> STATUS.NEW
            else -> STATUS.CANCELED
        }

        return Order(
            orderId = orderResp.orderHash ?: return null,
            pair = pair,
            price = price,
            origQty = baseAmount,
            executedQty = if (status == STATUS.FILLED) baseAmount else BigDecimal.ZERO,
            side = side,
            type = TYPE.LIMIT,
            status = status
        )
    }

    fun stopPolling() {
        running = false
        interrupt()
    }

    fun trackOrder(orderHash: String) {
        knownOrderStates[orderHash] = OrderState(
            orderHash = orderHash,
            remainingMakerAmount = "unknown",
            isActive = true,
            isFilled = false
        )
    }
}
