package bot.trade.balance

import bot.trade.exchanges.clients.Balance
import bot.trade.exchanges.clients.Candlestick
import bot.trade.exchanges.clients.Client
import bot.trade.exchanges.clients.CommonExchangeData
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.INTERVAL
import bot.trade.exchanges.clients.Order
import bot.trade.exchanges.clients.OrderBook
import bot.trade.exchanges.clients.STATUS
import bot.trade.exchanges.clients.TradePair
import bot.trade.exchanges.clients.stream.Stream
import java.util.concurrent.BlockingQueue

class StubClient : Client {
    override fun getAllPairs(): List<TradePair> = emptyList()
    override fun getOpenOrders(pair: TradePair): List<Order> = emptyList()
    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> = emptyMap()
    override fun getBalances(): Map<String, List<Balance>> = emptyMap()
    override fun getBalance(coin: String): Balance? {
        TODO("Not yet implemented")
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = OrderBook(emptyList(), emptyList())
    override fun getAssetBalance(asset: String): Map<String, Balance?> = emptyMap()
    override fun getOrder(pair: TradePair, orderId: String): Order? = null
    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> = emptyList()
    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int, start: Long?, end: Long?): List<Candlestick> = emptyList()
    override fun newOrder(order: Order, isStaticUpdate: Boolean, qty: String, price: String, positionSide: DIRECTION?, isReduceOnly: Boolean): Order {
        return order.copy(orderId = "stub-${System.currentTimeMillis()}", status = STATUS.NEW)
    }
    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean = true
    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream {
        // Return a stub stream implementation that extends Stream (which extends Thread)
        return object : Stream() {
            override fun run() {
                // Empty implementation - this thread does nothing
            }
        }
    }
    override fun close() {
        // Empty implementation for stub client
    }
}