package bot.trade.exchanges.clients

import bot.trade.exchanges.BotEvent
import bot.trade.exchanges.clients.stream.Stream
import java.util.concurrent.BlockingQueue

interface Client {
    fun getAllPairs(): List<TradePair>
    fun getOpenOrders(pair: TradePair): List<Order>
    fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>>
    fun getBalances(): Map<String, List<Balance>>?
    fun getOrderBook(pair: TradePair, limit: Int): OrderBook
    fun getAssetBalance(asset: String): Map<String, Balance?>
    fun getOrder(pair: TradePair, orderId: String): Order?
    fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick>
    fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick>

    fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION? = null,
        isReduceOnly: Boolean = false
    ): Order

    fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean

    /**
     * @return socket with thread (process for income messages)
     * @param pair trading pair
     * @param interval time interval for candlestick events
     * @param queue container for all income events
     * */
    fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream

    /**
     * close OkHttpClient
     * */
    fun close()
}