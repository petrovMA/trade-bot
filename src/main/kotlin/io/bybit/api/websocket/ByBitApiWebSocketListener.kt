package io.bybit.api.websocket

import com.neovisionaries.ws.client.*
import io.bybit.api.Authorization
import utils.mapper.Mapper.asObject
import utils.mapper.Mapper.asString
import io.bybit.api.websocket.messages.requests.WebSocketMsg
import io.bybit.api.websocket.messages.response.*
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private val orderPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"order")
    private val orderBookPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"orderBook")
    private val orderBookSnapshotPattern = Regex("\\s*\"type\"\\s*:\\s*\"snapshot\"")
    private val tradePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"publicTrade\\.")
    private val insurancePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"insurance")
    private val instrumentInfoPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"instrument_info")
    private val klinePattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"kline\\.\\d+")
    private val liquidationPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"liquidation")
    private val positionPattern = Regex("\\s*\\{\\s*\"topic\"\\s*:\\s*\"position")
    private val pongPattern = Regex(".+\"ret_msg\"\\s*:\\s*\"pong\".+\"op\"\\s*:\\s*\"ping\".+")
    private val subscribePattern = Regex(".+\"success\"\\s*:\\s*true.+\"ret_msg\"\\s*:\\s*\"\".+\"op\"\\s*:\\s*\"subscribe\".+")
    private val authPattern = Regex(".+\"success\"\\s*:\\s*true.+\"ret_msg\"\\s*:\\s*\"\".+\"op\"\\s*:\\s*\"auth\".+")

    /**
     * callBacks or every message type
     */
    private var orderBookCallback: ((OrderBook) -> Unit)? = null
    private var tradeCallback: ((Trade) -> Unit)? = null
    private var insuranceCallback: ((Insurance) -> Unit)? = null
    private var instrumentInfoCallback: ((InstrumentInfo) -> Unit)? = null
    private var klineCallback: ((Kline) -> Unit)? = null
    private var liquidationCallback: ((Liquidation) -> Unit)? = null
    private var orderCallback: ((Order) -> Unit)? = null
    private var positionCallback: ((Position) -> Unit)? = null

    private var schedulerReconnect = Executors.newScheduledThreadPool(1)
    private val url: String
    private var lastMessageTime: Long = 0L

    /**
     * Initialize listener for authorized user
     */
    constructor(
        api: String,
        sec: String,
        url: String,
        timeout: Int = TIMEOUT,
        keepConnection: Boolean = true,
        pingTimeInterval: Duration? = null,
        vararg subscribeMessages: WebSocketMsg
    ) {
        this.keepConnection = keepConnection

        this.url = url

        webSocket = connect(
            api = api,
            sec = sec,
            url = url,
            timeout = timeout,
            pingTimeInterval = pingTimeInterval,
            subscribeMessages = subscribeMessages
        )

        schedulerReconnect.scheduleAtFixedRate({
            log.debug("Reconnecting...")
            webSocket?.disconnect()
            webSocket = connect(
                api = api,
                sec = sec,
                url = url,
                timeout = timeout,
                pingTimeInterval = pingTimeInterval,
                subscribeMessages = subscribeMessages
            )
        }, 9, 9, TimeUnit.MINUTES)
    }

    /**
     * Initialize listener for common messages
     */
    constructor(
        url: String,
        timeout: Int = TIMEOUT,
        keepConnection: Boolean = true,
        pingTimeInterval: Duration? = null,
        reconnectIfNoMessagesDuring: Duration,
        vararg subscribeMessages: WebSocketMsg
    ) {
        this.keepConnection = keepConnection
        this.url = url

        webSocket = connect(
            url = url,
            timeout = timeout,
            pingTimeInterval = pingTimeInterval,
            subscribeMessages = subscribeMessages
        )

        schedulerReconnect.scheduleAtFixedRate({
            log.debug("Check connection")
            if (System.currentTimeMillis() - lastMessageTime > reconnectIfNoMessagesDuring.toMillis()) {
                log.debug("Reconnecting...")
                webSocket?.disconnect()
                webSocket = connect(
                    url = url,
                    timeout = timeout,
                    pingTimeInterval = pingTimeInterval,
                    subscribeMessages = subscribeMessages
                )
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    fun disconnect() {
        webSocket?.disconnect()
        schedulerReconnect.shutdown()
    }

    private fun connect(
        timeout: Int = TIMEOUT,
        api: String? = null,
        sec: String? = null,
        url: String,
        pingTimeInterval: Duration? = null,
        vararg subscribeMessages: WebSocketMsg
    ): WebSocket {
        val authMsg = if (api != null && sec != null) {
            val date = (System.currentTimeMillis() + 5000).toString()
            WebSocketMsg("auth", listOf(api, date, Authorization.signForWebSocket("GET/realtime$date", sec)))
        } else null

        try {
            val webSocket = WebSocketFactory()
                .setConnectionTimeout(timeout)
                .createSocket(url)
                .addListener(object : WebSocketAdapter() {

                    override fun onPongFrame(websocket: WebSocket?, frame: WebSocketFrame?) {
                        log.debug("Pong message received!")
                        lastMessageTime = System.currentTimeMillis()
                    }

                    override fun onTextMessage(websocket: WebSocket, message: String) {
                        onMessage(message)
                    }

                    override fun onDisconnected(
                        websocket: WebSocket, serverCloseFrame: WebSocketFrame,
                        clientCloseFrame: WebSocketFrame, closedByServer: Boolean
                    ) {
                        log.debug("Socket Disconnected!")
                    }
                })
                .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)

            pingTimeInterval?.let { webSocket.pingInterval = it.toMillis() }
            log.debug("Connecting to $url")
            webSocket.connect()
            authMsg?.let {
                val text = asString(it)
                log.trace("Send message >>> $text")
                webSocket.sendText(text)
            }
            subscribeMessages.forEach { webSocket.sendText(asString(it)) }
            return webSocket
        } catch (e: Exception) {
            log.error("Can't connect to $url", e)
            throw e
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
        lastMessageTime = System.currentTimeMillis()
        try {
            when {
                message.matches(pongPattern) -> log.debug("Pong message received!")
                message.matches(authPattern) -> log.debug("Auth success message received!")
                message.matches(subscribePattern) -> log.debug("Subscribe success message received!")
                tradePattern.containsMatchIn(message) -> tradeCallback?.invoke(asObject(message))
                orderPattern.containsMatchIn(message) -> orderCallback?.invoke(asObject(message))
                positionPattern.containsMatchIn(message) -> positionCallback?.invoke(asObject(message))
                orderBookPattern.containsMatchIn(message) ->
                    if (orderBookSnapshotPattern.containsMatchIn(message)) {
                        asObject<OrderBookSnapshot>(message).run {
                            OrderBook(
                                cross_seq = cross_seq,
                                timestamp_e6 = timestamp_e6,
                                topic = topic,
                                type = type,
                                data = OrderBook.Data(
                                    insert = data,
                                    update = emptyList(),
                                    delete = emptyList(),
                                    transactTimeE6 = timestamp_e6
                                )
                            ).let { orderBookCallback?.invoke(it) }
                        }
                    } else orderBookCallback?.invoke(asObject(message))

                insurancePattern.containsMatchIn(message) -> insuranceCallback?.invoke(asObject(message))
                instrumentInfoPattern.containsMatchIn(message) -> instrumentInfoCallback?.invoke(asObject(message))
                klinePattern.containsMatchIn(message) -> klineCallback?.invoke(asObject(message))
                liquidationPattern.containsMatchIn(message) -> liquidationCallback?.invoke(asObject(message))
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

    fun setTradeCallback(tradeCallback: (Trade) -> Unit) = apply { this.tradeCallback = tradeCallback }

    fun setInsuranceCallback(insuranceCallback: (Insurance) -> Unit) =
        apply { this.insuranceCallback = insuranceCallback }

    fun setInstrumentInfoCallback(instrumentInfoCallback: (InstrumentInfo) -> Unit) =
        apply { this.instrumentInfoCallback = instrumentInfoCallback }

    fun setKlineCallback(klineCallback: (Kline) -> Unit) = apply { this.klineCallback = klineCallback }

    fun setLiquidationCallback(liquidationCallback: (Liquidation) -> Unit) =
        apply { this.liquidationCallback = liquidationCallback }

    fun setOrderCallback(orderCallback: (Order) -> Unit) = apply { this.orderCallback = orderCallback }

    fun setPositionCallback(positionCallback: (Position) -> Unit) = apply { this.positionCallback = positionCallback }

    companion object {
        /**
         * The timeout value in milliseconds for socket connection.
         */
        private const val TIMEOUT = 5000
    }
}