package io.bybit.api.websocket

import com.neovisionaries.ws.client.*
import io.bybit.api.Authorization
import utils.mapper.Mapper.asObject
import utils.mapper.Mapper.asString
import io.bybit.api.websocket.messages.requests.WebSocketMsg
//import io.bybit.api.websocket.messages.responses.*
import mu.KotlinLogging
import java.util.regex.Pattern

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
    private val pingPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"ping\"\\s*")
    private val summaryPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"summary\"\\s*")
    private val depthPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"depth\"\\s*")
    private val marketTradesPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"trades\"\\s*")
    private val barPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"bar\"\\s*")
    private val pongPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"pong\"\\s*}")
    private val orderPattern = Pattern.compile("\\s*\\{\\s*\"m\"\\s*:\\s*\"order\"\\s*")

    /**
     * callBacks or every message type
     */
//    private var summaryCallback: ((WebSocketSummary) -> Unit)? = null
//    private var depthCallback: ((WebSocketDepth) -> Unit)? = null
//    private var marketTradesCallback: ((WebSocketMarketTrades) -> Unit)? = null
//    private var barCallback: ((WebSocketBar) -> Unit)? = null
//    private var orderCallback: ((WebSocketOrder) -> Unit)? = null

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
//        when {
//            pingPattern.matcher(message).find() -> {
//                if (keepConnection) sendText("{ \"op\": \"pong\" }")
//            }
//            summaryPattern.matcher(message).find() ->
//                summaryCallback?.invoke(asObject(message, WebSocketSummary::class.java))
//            depthPattern.matcher(message).find() ->
//                depthCallback?.invoke(asObject(message, WebSocketDepth::class.java))
//            marketTradesPattern.matcher(message).find() ->
//                marketTradesCallback?.invoke(asObject(message, WebSocketMarketTrades::class.java))
//            barPattern.matcher(message).find() ->
//                barCallback?.invoke(asObject(message, WebSocketBar::class.java))
//            orderPattern.matcher(message).find() ->
//                orderCallback?.invoke(asObject(message, WebSocketOrder::class.java))
//        }
    }

    fun close() {
        webSocket!!.disconnect()
    }

//    fun setSummaryCallback(summaryCallback: (WebSocketSummary) -> Unit) {
//        this.summaryCallback = summaryCallback
//    }
//
//    fun setDepthCallback(depthCallback: (WebSocketDepth) -> Unit) {
//        this.depthCallback = depthCallback
//    }
//
//    fun setMarketTradesCallback(marketTradesCallback: (WebSocketMarketTrades) -> Unit) {
//        this.marketTradesCallback = marketTradesCallback
//    }
//
//    fun setBarCallback(barCallback: (WebSocketBar) -> Unit) {
//        this.barCallback = barCallback
//    }
//
//    fun setOrderCallback(orderCallback: (WebSocketOrder) -> Unit) {
//        this.orderCallback = orderCallback
//    }

    companion object {
        /**
         * The timeout value in milliseconds for socket connection.
         */
        private const val TIMEOUT = 5000
    }
}