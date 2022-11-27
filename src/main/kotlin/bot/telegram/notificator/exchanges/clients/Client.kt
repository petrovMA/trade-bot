package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.exchanges.BotEvent
import bot.telegram.notificator.exchanges.clients.socket.SocketThread
import java.util.concurrent.BlockingQueue

interface Client {
    fun getAllPairs(): List<TradePair>
    fun getOpenOrders(pair: TradePair): List<Order>
    fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>>
    fun getBalances(): List<Balance>
    fun getOrderBook(pair: TradePair, limit: Int): OrderBook
    fun getAssetBalance(asset: String): Balance
    fun getOrder(pair: TradePair, orderId: String): Order?
    fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick>
    fun newOrder(order: Order, isStaticUpdate: Boolean, formatCount: String, formatPrice: String): Order
    fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean

    /**
     * @return socket with thread (process for income messages)
     * @param pair trading pair
     * @param interval time interval for candlestick events
     * @param queue container for all income events
     * */
    fun socket(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): SocketThread

    /**
     * This method only for tests (emulate) trading
     * Adds Event to socket queue
     * */
    fun nextEvent(): CommonExchangeData = BotEvent(type = BotEvent.Type.INTERRUPT)

    /**
     * close OkHttpClient
     * */
    fun close()
}