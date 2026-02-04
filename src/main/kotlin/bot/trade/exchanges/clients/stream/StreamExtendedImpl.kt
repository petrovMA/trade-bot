package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mu.KotlinLogging
import okhttp3.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StreamExtendedImpl(
    val pair: TradePair,
    private val queue: BlockingQueue<CommonExchangeData>,
    private val apiKey: String?
) : Stream() {

    private val log = KotlinLogging.logger {}
    private val gson = Gson()
    private val wsBaseUrl = "wss://api.starknet.extended.exchange/stream.extended.exchange/v1"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var publicWs: WebSocket? = null
    private var privateWs: WebSocket? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var reconnectTask: ScheduledFuture<*>? = null

    @Volatile
    private var isRunning = true

    private val market = "${pair.first}-${pair.second}"

    override fun run() {
        log.info("StreamExtended: Starting stream for $market")
        connectPublic()
        if (apiKey != null) {
            connectPrivate()
        }
        startReconnectMonitor()
    }

    private fun connectPublic() {
        val request = Request.Builder()
            .url(wsBaseUrl)
            .build()

        publicWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.info("StreamExtended: Public WebSocket connected")
                subscribeTrades(webSocket)
                subscribeOrderBook(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handlePublicMessage(text)
                } catch (e: Exception) {
                    log.error("StreamExtended: Error handling public message: ${e.message}", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.error("StreamExtended: Public WebSocket failure: ${t.message}", t)
                if (isRunning) {
                    scheduleReconnectPublic()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log.info("StreamExtended: Public WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.info("StreamExtended: Public WebSocket closed: $code $reason")
                if (isRunning) {
                    scheduleReconnectPublic()
                }
            }
        })
    }

    private fun connectPrivate() {
        val request = Request.Builder()
            .url(wsBaseUrl)
            .addHeader("X-Api-Key", apiKey!!)
            .build()

        privateWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.info("StreamExtended: Private WebSocket connected")
                subscribeOrders(webSocket)
                subscribePositions(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handlePrivateMessage(text)
                } catch (e: Exception) {
                    log.error("StreamExtended: Error handling private message: ${e.message}", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.error("StreamExtended: Private WebSocket failure: ${t.message}", t)
                if (isRunning) {
                    scheduleReconnectPrivate()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log.info("StreamExtended: Private WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.info("StreamExtended: Private WebSocket closed: $code $reason")
                if (isRunning) {
                    scheduleReconnectPrivate()
                }
            }
        })
    }

    private fun subscribeTrades(ws: WebSocket) {
        val msg = gson.toJson(mapOf(
            "op" to "subscribe",
            "channel" to "trades",
            "market" to market
        ))
        ws.send(msg)
        log.info("StreamExtended: Subscribed to trades for $market")
    }

    private fun subscribeOrderBook(ws: WebSocket) {
        val msg = gson.toJson(mapOf(
            "op" to "subscribe",
            "channel" to "orderbook",
            "market" to market
        ))
        ws.send(msg)
        log.info("StreamExtended: Subscribed to orderbook for $market")
    }

    private fun subscribeOrders(ws: WebSocket) {
        val msg = gson.toJson(mapOf(
            "op" to "subscribe",
            "channel" to "orders"
        ))
        ws.send(msg)
        log.info("StreamExtended: Subscribed to orders")
    }

    private fun subscribePositions(ws: WebSocket) {
        val msg = gson.toJson(mapOf(
            "op" to "subscribe",
            "channel" to "positions"
        ))
        ws.send(msg)
        log.info("StreamExtended: Subscribed to positions")
    }

    private fun handlePublicMessage(text: String) {
        val json = JsonParser.parseString(text).asJsonObject
        val channel = json.get("channel")?.asString ?: return

        when (channel) {
            "trades" -> handleTradeMessage(json)
            "orderbook" -> handleOrderBookMessage(json)
        }
    }

    private fun handlePrivateMessage(text: String) {
        val json = JsonParser.parseString(text).asJsonObject
        val channel = json.get("channel")?.asString ?: return

        when (channel) {
            "orders" -> handleOrderUpdateMessage(json)
            "positions" -> handlePositionUpdateMessage(json)
        }
    }

    private fun handleTradeMessage(json: JsonObject) {
        val data = json.getAsJsonArray("data") ?: return
        data.forEach { element ->
            val trade = element.asJsonObject
            val price = trade.get("p")?.asString?.toBigDecimalOrNull()
                ?: trade.get("price")?.asString?.toBigDecimalOrNull() ?: return@forEach
            val qty = trade.get("q")?.asString?.toBigDecimalOrNull()
                ?: trade.get("quantity")?.asString?.toBigDecimalOrNull() ?: return@forEach
            val time = trade.get("T")?.asLong
                ?: trade.get("timestamp")?.asLong ?: System.currentTimeMillis()
            queue.add(Trade(price, qty, time))
        }
    }

    private fun handleOrderBookMessage(json: JsonObject) {
        val data = json.getAsJsonObject("data") ?: return
        val bids = data.getAsJsonArray("bids")
        val asks = data.getAsJsonArray("asks")

        if (bids != null && bids.size() > 0 && asks != null && asks.size() > 0) {
            val bestBid = bids[0].asJsonArray
            val bestAsk = asks[0].asJsonArray
            queue.add(
                DepthEventOrders(
                    ask = Offer(bestAsk[0].asString.toBigDecimal(), bestAsk[1].asString.toBigDecimal()),
                    bid = Offer(bestBid[0].asString.toBigDecimal(), bestBid[1].asString.toBigDecimal())
                )
            )
        }
    }

    private fun handleOrderUpdateMessage(json: JsonObject) {
        val data = json.getAsJsonObject("data") ?: return
        val orderMarket = data.get("market")?.asString ?: return
        val orderPair = TradePair(orderMarket)

        if (orderPair != pair) return

        val orderId = data.get("id")?.asString ?: return
        val side = data.get("side")?.asString?.uppercase() ?: return
        val status = data.get("status")?.asString ?: return
        val price = data.get("price")?.asString?.toBigDecimalOrNull()
            ?: data.get("avgFillPrice")?.asString?.toBigDecimalOrNull()
        val origQty = data.get("quantity")?.asString?.toBigDecimalOrNull() ?: return
        val filledQty = data.get("filledQuantity")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val fee = data.get("fee")?.asString?.toBigDecimalOrNull()

        val order = Order(
            orderId = orderId,
            pair = orderPair,
            price = price,
            origQty = origQty,
            executedQty = filledQty,
            side = SIDE.fromString(side),
            type = when (data.get("type")?.asString?.uppercase()) {
                "MARKET" -> TYPE.MARKET
                "LIMIT" -> TYPE.LIMIT
                else -> TYPE.LIMIT
            },
            status = mapOrderStatus(status),
            fee = fee
        )
        queue.add(order)
    }

    private fun handlePositionUpdateMessage(json: JsonObject) {
        val data = json.getAsJsonObject("data") ?: return
        val posMarket = data.get("market")?.asString ?: return
        val posPair = TradePair(posMarket)

        if (posPair != pair) return

        val position = Position(
            pair = posPair,
            marketPrice = data.get("markPrice")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            unrealisedPnl = data.get("unrealizedPnl")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            realisedPnl = data.get("realizedPnl")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            entryPrice = data.get("entryPrice")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            breakEvenPrice = data.get("entryPrice")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            leverage = data.get("leverage")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ONE,
            liqPrice = data.get("liquidationPrice")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            size = data.get("size")?.asString?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO,
            side = data.get("side")?.asString ?: "NONE"
        )
        queue.add(position)
    }

    private fun mapOrderStatus(status: String): STATUS = when (status.uppercase()) {
        "NEW", "OPEN" -> STATUS.NEW
        "PARTIALLY_FILLED" -> STATUS.PARTIALLY_FILLED
        "FILLED" -> STATUS.FILLED
        "CANCELLED", "CANCELED" -> STATUS.CANCELED
        "REJECTED" -> STATUS.REJECTED
        else -> STATUS.UNSUPPORTED
    }

    private fun startReconnectMonitor() {
        reconnectTask = scheduler.scheduleAtFixedRate({
            try {
                if (!isRunning) return@scheduleAtFixedRate
                // Ping is handled by OkHttp's pingInterval
            } catch (e: Exception) {
                log.error("StreamExtended: Reconnect monitor error: ${e.message}", e)
            }
        }, 60, 60, TimeUnit.SECONDS)
    }

    private fun scheduleReconnectPublic() {
        if (!isRunning) return
        scheduler.schedule({
            if (isRunning) {
                log.info("StreamExtended: Reconnecting public WebSocket...")
                connectPublic()
            }
        }, 5, TimeUnit.SECONDS)
    }

    private fun scheduleReconnectPrivate() {
        if (!isRunning) return
        scheduler.schedule({
            if (isRunning) {
                log.info("StreamExtended: Reconnecting private WebSocket...")
                connectPrivate()
            }
        }, 5, TimeUnit.SECONDS)
    }

    override fun interrupt() {
        isRunning = false
        reconnectTask?.cancel(false)
        scheduler.shutdownNow()
        publicWs?.close(1000, "Shutting down")
        privateWs?.close(1000, "Shutting down")
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        super.interrupt()
        log.info("StreamExtended: Stream interrupted for $market")
    }
}
