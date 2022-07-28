package io.bybit.api.websocket

import com.neovisionaries.ws.client.*
import io.bybit.api.Authorization
import utils.mapper.Mapper.asObject
import utils.mapper.Mapper.asString
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import io.bybit.api.websocket.messages.response.instrument_info.InstrumentInfo
import io.bybit.api.websocket.messages.response.insurance.Insurance
import io.bybit.api.websocket.messages.response.kline.Kline
import io.bybit.api.websocket.messages.response.liquidation.Liquidation
import io.bybit.api.websocket.messages.response.order_book.Data
import io.bybit.api.websocket.messages.response.order_book.OrderBook
import io.bybit.api.websocket.messages.response.order_book.OrderBookSnapshot
import io.bybit.api.websocket.messages.response.trade.Trade
import mu.KotlinLogging

/**
 * Represents a Listener of webSocket channels
 */
class ByBitApiWebSocketListener {
    /**
     * WebSocket executor service, execute webSocket tasks such as open/close channel and send message
     */
    private var webSocket: WebSocket? = null
    private var keepConnection: Boolean = false
    private val log = KotlinLogging.logger {}

    /**
     * patterns to determine type of message
     */
    private val orderBookPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"orderBook")
    private val orderBookSnapshotPattern = Regex("\\s*\"type\"\\s*:\\s*\"snapshot\"")
    private val tradePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"trade\\.")
    private val insurancePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"insurance")
    private val instrumentInfoPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"instrument_info")
    private val klinePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"klineV2")
    private val liquidationPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"liquidation")

    /**
     * callBacks or every message type
     */
    private var orderBookCallback: ((OrderBook) -> Unit)? = null
    private var tradeCallback: ((Trade) -> Unit)? = null
    private var insuranceCallback: ((Insurance) -> Unit)? = null
    private var instrumentInfoCallback: ((InstrumentInfo) -> Unit)? = null
    private var klineCallback: ((Kline) -> Unit)? = null
    private var liquidationCallback: ((Liquidation) -> Unit)? = null

    /**
     * Initialize listener for authorized user
     */
    constructor(
        api: String,
        sec: String,
        url: String,
        timeout: Int = TIMEOUT,
        keepConnection: Boolean = true,
        vararg subscribeMessages: WebSocketMsg
    ) {
        this.keepConnection = keepConnection

        val date = (System.currentTimeMillis() + 5000).toString()
        val authMsg = WebSocketMsg("auth", listOf(api, date, Authorization.signForWebSocket("GET/realtime$date", sec)))
        try {
            webSocket = WebSocketFactory()
                .setConnectionTimeout(timeout)
                .createSocket(url)
                .addListener(object : WebSocketAdapter() {
                    override fun onTextMessage(websocket: WebSocket, message: String) {
                        onMessage(message)
                    }

                    override fun onDisconnected(
                        websocket: WebSocket, serverCloseFrame: WebSocketFrame,
                        clientCloseFrame: WebSocketFrame, closedByServer: Boolean
                    ) {
                        println("Socket Disconnected!")
                    }
                })
                .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
            webSocket!!.connect()
            send(authMsg)
            subscribeMessages.forEach { send(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Initialize listener for common messages
     */
    constructor(
        url: String,
        timeout: Int = TIMEOUT,
        keepConnection: Boolean = true,
        vararg subscribeMessages: WebSocketMsg
    ) {
        this.keepConnection = keepConnection
        try {
            webSocket = WebSocketFactory()
                .setConnectionTimeout(timeout)
                .createSocket(url)
                .addListener(object : WebSocketAdapter() {
                    override fun onTextMessage(websocket: WebSocket, message: String) {
                        onMessage(message)
                    }

                    override fun onDisconnected(
                        websocket: WebSocket, serverCloseFrame: WebSocketFrame,
                        clientCloseFrame: WebSocketFrame, closedByServer: Boolean
                    ) {
                        println("Socket Disconnected!")
                    }
                })
                .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
            webSocket!!.connect()
            subscribeMessages.forEach { send(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun send(message: Any) {
        val text = asString(message)
        log.trace("Send message >>> $text")
        webSocket!!.sendText(asString(message))
    }

    fun sendText(message: String) {
        log.trace("Send message >>> $message")
        webSocket!!.sendText(message)
    }

    private fun onMessage(message: String) {
        log.trace("Receive message <<< $message")
        try {
            when {
//            pingPattern.matcher(message).find() -> {
//                if (keepConnection) sendText("{ \"op\": \"pong\" }")
//            }
                orderBookPattern.containsMatchIn(message) ->
                    if (orderBookSnapshotPattern.containsMatchIn(message)) {
                        asObject(message, OrderBookSnapshot::class.java).run {
                            OrderBook(
                                cross_seq = cross_seq,
                                timestamp_e6 = timestamp_e6,
                                topic = topic,
                                type = type,
                                data = Data(
                                    insert = data,
                                    update = emptyList(),
                                    delete = emptyList(),
                                    transactTimeE6 = timestamp_e6
                                )
                            ).let { orderBookCallback?.invoke(it) }
                        }
                    } else
                        orderBookCallback?.invoke(asObject(message, OrderBook::class.java))
                tradePattern.containsMatchIn(message) ->
                    tradeCallback?.invoke(asObject(message, Trade::class.java))
                insurancePattern.containsMatchIn(message) ->
                    insuranceCallback?.invoke(asObject(message, Insurance::class.java))
                instrumentInfoPattern.containsMatchIn(message) ->
                    instrumentInfoCallback?.invoke(asObject(message, InstrumentInfo::class.java))
                klinePattern.containsMatchIn(message) ->
                    klineCallback?.invoke(asObject(message, Kline::class.java))
                liquidationPattern.containsMatchIn(message) ->
                    liquidationCallback?.invoke(asObject(message, Liquidation::class.java))
                else -> log.warn { "Not found pattern for message: $message" }
            }
        } catch (t: Throwable) {
            log.error("Can't deserialize message: $message", t)
        }
    }

    fun close() {
        webSocket!!.disconnect()
    }

    fun setOrderBookCallback(orderBookCallback: (OrderBook) -> Unit) =
        apply { this.orderBookCallback = orderBookCallback }

    fun setTradeCallback(tradeCallback: (Trade) -> Unit) =
        apply { this.tradeCallback = tradeCallback }

    fun setInsuranceCallback(insuranceCallback: (Insurance) -> Unit) =
        apply { this.insuranceCallback = insuranceCallback }

    fun setInstrumentInfoCallback(instrumentInfoCallback: (InstrumentInfo) -> Unit) =
        apply { this.instrumentInfoCallback = instrumentInfoCallback }

    fun setKlineCallback(klineCallback: (Kline) -> Unit) =
        apply { this.klineCallback = klineCallback }

    fun setLiquidationCallback(liquidationCallback: (Liquidation) -> Unit) =
        apply { this.liquidationCallback = liquidationCallback }

    companion object {
        /**
         * The timeout value in milliseconds for socket connection.
         */
        private const val TIMEOUT = 5000
    }
}