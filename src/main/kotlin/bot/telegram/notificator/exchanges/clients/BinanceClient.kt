package bot.telegram.notificator.exchanges.clients

//import bot.telegram.notificator.exchange.clients.socket.SocketThreadBinanceImpl
import mu.KotlinLogging
import org.knowm.xchange.ExchangeFactory
import org.knowm.xchange.binance.BinanceExchange
import java.util.concurrent.BlockingQueue
import org.knowm.xchange.Exchange
import org.knowm.xchange.binance.service.BinanceTradeService


//typealias BinanceOrder = com.binance.api.client.domain.account.Order
//typealias BinanceCandlestick = com.binance.api.client.domain.market.Candlestick
//typealias BinanceOrderBook = com.binance.api.client.domain.market.OrderBook

class BinanceClient(
    val api: String? = null,
    val sec: String? = null,
    instance: Exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange::class.java, api, sec)
) : Client {

    private val tradeService: BinanceTradeService = instance.tradeService as (BinanceTradeService)

    private val log = KotlinLogging.logger {}

    override fun getOpenOrders(pair: TradePair): List<Order> = TODO("not implemented")
//        instance
//            .getOpenOrders(OrderRequest(pair.first + pair.second))
//            .map { it.toOrder(pair) }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<String, List<Order>> = TODO("not implemented")
//        pairs
//            .map { getOpenOrders(it) }
//            .flatten()
//            .groupBy { it.pair.toString() }

    override fun getBalances(): List<Balance> = TODO("not implemented")
//    instance.account.balances.map { it.toBalance() }

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = TODO("not implemented")
//        instance
//            .getOrderBook(pair.first + pair.second, limit)
//            .toOrderBook()

    override fun getAssetBalance(asset: String): Balance = TODO("not implemented")
//        instance.account
//            .getAssetBalance(asset)
//            .toBalance()

    override fun getOrder(pair: TradePair, orderId: String): Order = TODO("not implemented")
//            instance.getOrderStatus(OrderStatusRequest(pair.first + pair.second, orderId)).toOrder(pair)

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> = TODO("not implemented")
//        instance
//            .getCandlestickBars(pair.first + pair.second, interval.toCandlestickInterval(), countCandles, null, null)
//            .map { it.toCandlestick() }

    override fun newOrder(
        pair: TradePair,
        side: SIDE,
        type: TYPE,
        amount: Double,
        price: Double,
        isStaticUpdate: Boolean,
        formatCount: String,
        formatPrice: String
    ): Order {
        TODO("not implemented")
        /*val formattedCount = java.lang.String.format(formatCount, amount).replace(",", ".")
        val formattedPrice = java.lang.String.format(formatPrice, price).replace(",", ".")

        return instance.newOrder(
            NewOrder(
                pair.first + pair.second,
                when (side) {
                    SIDE.BUY -> OrderSide.BUY
                    SIDE.SELL -> OrderSide.SELL
                    else -> throw UnsupportedOrderSideException()
                },
                when (type) {
                    TYPE.LIMIT -> OrderType.LIMIT
                    TYPE.MARKET -> OrderType.MARKET
                    else -> throw UnsupportedOrderTypeException()
                },
                TimeInForce.GTC,
                formattedCount,
                formattedPrice
            )
        ).toBinanceOrder().toOrder(pair)*/
    }


    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean) = TODO("not implemented")
//            instance.cancelOrder(CancelOrderRequest(pair.first + pair.second, orderId))

    override fun getAllPairs() = TODO("not implemented")
    // instance.exchangeInfo.symbols.map { TradePair(it.baseAsset, it.quoteAsset) }

    override fun socket(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        TODO("not implemented")
//        SocketThreadBinanceImpl(
//        "${pair.first.toLowerCase()}${pair.second.toLowerCase()}",
//        BinanceApiClientFactory.newInstance().newWebSocketClient(),
//        interval.toCandlestickInterval(),
//        queue
//    )

    override fun nextEvent() {}
    override fun close() {}
}
//
//fun AssetBalance.toBalance(): Balance = Balance(
//    asset = asset,
//    total = free.toDouble() + locked.toDouble(),
//    free = free.toDouble(),
//    locked = locked.toDouble()
//)
//
//fun NewOrderResponse.toBinanceOrder(): BinanceOrder = BinanceOrder(
//    this.symbol,
//    this.orderId,
//    this.clientOrderId,
//    this.price,
//    this.origQty,
//    this.executedQty,
//    this.status,
//    this.timeInForce,
//    this.type,
//    this.side
//)
//
//fun INTERVAL.toCandlestickInterval(): CandlestickInterval = when (this) {
//    INTERVAL.ONE_MINUTE -> CandlestickInterval.ONE_MINUTE
//    INTERVAL.THREE_MINUTES -> CandlestickInterval.THREE_MINUTES
//    INTERVAL.FIVE_MINUTES -> CandlestickInterval.FIVE_MINUTES
//    INTERVAL.FIFTEEN_MINUTES -> CandlestickInterval.FIFTEEN_MINUTES
//    INTERVAL.HALF_HOURLY -> CandlestickInterval.HALF_HOURLY
//    INTERVAL.HOURLY -> CandlestickInterval.HOURLY
//    INTERVAL.TWO_HOURLY -> CandlestickInterval.TWO_HOURLY
//    INTERVAL.FOUR_HOURLY -> CandlestickInterval.FOUR_HOURLY
//    INTERVAL.SIX_HOURLY -> CandlestickInterval.SIX_HOURLY
//    INTERVAL.EIGHT_HOURLY -> CandlestickInterval.EIGHT_HOURLY
//    INTERVAL.TWELVE_HOURLY -> CandlestickInterval.TWELVE_HOURLY
//    INTERVAL.DAILY -> CandlestickInterval.DAILY
//    INTERVAL.THREE_DAILY -> CandlestickInterval.THREE_DAILY
//    INTERVAL.WEEKLY -> CandlestickInterval.WEEKLY
//    INTERVAL.MONTHLY -> CandlestickInterval.MONTHLY
//}
//
//fun BinanceOrder.toOrder(pair: TradePair): Order = Order(
//    pair = pair,
//    orderId = this.clientOrderId,
//    price = this.price.toDouble(),
//    origQty = this.origQty.toDouble(),
//    executedQty = this.executedQty.toDouble(),
//    status = when (this.status) {
//        OrderStatus.PARTIALLY_FILLED -> STATUS.PARTIALLY_FILLED
//        OrderStatus.FILLED -> STATUS.FILLED
//        OrderStatus.CANCELED -> STATUS.CANCELED
//        OrderStatus.NEW -> STATUS.NEW
//        OrderStatus.REJECTED -> STATUS.REJECTED
//        else -> STATUS.UNSUPPORTED
//    },
//    type = when (this.type) {
//        OrderType.LIMIT -> TYPE.LIMIT
//        OrderType.MARKET -> TYPE.MARKET
//        else -> TYPE.UNSUPPORTED
//    },
//    side = when (this.side) {
//        OrderSide.SELL -> SIDE.SELL
//        OrderSide.BUY -> SIDE.BUY
//        else -> SIDE.UNSUPPORTED
//    }
//)
//
//fun BinanceCandlestick.toCandlestick(): Candlestick = Candlestick(
//    openTime = openTime.toLong(),
//    closeTime = closeTime.toLong(),
//    open = open.toDouble(),
//    high = high.toDouble(),
//    low = low.toDouble(),
//    close = close.toDouble(),
//    volume = volume.toDouble()
//)
//
//fun BinanceOrderBook.toOrderBook(): OrderBook = OrderBook(
//    asks = asks.map { OrderEntry(price = it.price.toDouble(), qty = it.qty.toDouble()) },
//    bids = bids.map { OrderEntry(price = it.price.toDouble(), qty = it.qty.toDouble()) }
//)
//
//fun File.toCandlestick(): List<Candlestick> = readObjectFromFile(this, ArrayList::class.java)
//        .map { it as BinanceCandlestick }
//        .map { it.toCandlestick() }