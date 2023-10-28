package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamThreadStub
import java.util.concurrent.BlockingQueue

class ClientTestExchange : Client {
    override fun getAllPairs(): List<TradePair> {
        TODO("Not yet implemented")
    }

    override fun getOpenOrders(pair: TradePair): List<Order> {
        TODO("Not yet implemented")
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> {
        TODO("Not yet implemented")
    }

    override fun getBalances(): Map<String, List<Balance>>? {
        TODO("Not yet implemented")
    }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook {
        TODO("Not yet implemented")
    }

    override fun getAssetBalance(asset: String): Map<String, Balance?> {
        TODO("Not yet implemented")
    }

    override fun getOrder(pair: TradePair, orderId: String): Order? {
        TODO("Not yet implemented")
    }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {
        TODO("Not yet implemented")
    }

    override fun newOrder(order: Order, isStaticUpdate: Boolean, formatCount: String, formatPrice: String): Order {
        TODO("Not yet implemented")
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream = StreamThreadStub()

    override fun close() {
        TODO("Not yet implemented")
    }

}