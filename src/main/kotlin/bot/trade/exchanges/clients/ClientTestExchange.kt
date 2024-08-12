package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamThreadStub
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class ClientTestExchange : ClientFutures {

    val orders: MutableList<Order> = mutableListOf()
    private var orderIds = 1

    private var position: Position = Position(
        pair = TradePair("BTC_USDT"),
        marketPrice = BigDecimal(20000),
        unrealisedPnl = BigDecimal(0),
        realisedPnl = BigDecimal(0),
        entryPrice = BigDecimal(20000),
        breakEvenPrice = BigDecimal(20000),
        leverage = BigDecimal(1),
        liqPrice = BigDecimal(1000),
        size = BigDecimal(1000),
        side = "None"
    )
    private val candlesticksData: MutableList<Candlestick> = mutableListOf()

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

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        candlesticksData

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> = candlesticksData

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        val newOrder = Order(
            orderId = (++orderIds).toString(),
            pair = order.pair,
            price = order.price,
            origQty = order.origQty,
            executedQty = order.executedQty,
            side = order.side,
            type = order.type,
            status = order.status,
            stopPrice = order.stopPrice,
        )

        orders.add(newOrder)
        return newOrder
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun stream(pairs: List<TradePair>, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream =
        StreamThreadStub()

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun switchMode(category: String, mode: Int, pair: TradePair?, coin: String?) {}

    override fun getPositions(pair: TradePair): List<Position> = listOf(position)

    fun addKlineData(candlesticks: List<Candlestick>) = candlesticksData.addAll(candlesticks)
    fun setPosition(position: Position) {
        this.position = position
    }
}