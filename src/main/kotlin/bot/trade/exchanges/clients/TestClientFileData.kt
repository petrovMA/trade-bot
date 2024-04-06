package bot.trade.exchanges.clients

import bot.trade.exchanges.BotEvent
import bot.trade.exchanges.clients.stream.StreamThreadStub
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.BlockingQueue

class TestClientFileData(
    val handler: (CommonExchangeData?) -> Unit,
    val params: BotEmulateParams,
    private val fee: BigDecimal = BigDecimal(0.1)
) : Client {
    private val log = KotlinLogging.logger {}
    private var orders: MutableMap<String, Order> = HashMap()
    var lastSellPrice: BigDecimal = BigDecimal(0)
        private set
    private var lastBuyPrice: BigDecimal = BigDecimal(0)
    private val profit = TestBalance(tradePair = params.botParams.pair)

    private var positionLong: Position = Position(
        pair = TradePair("TEST_PAIR"),
        marketPrice = 0.0.toBigDecimal(),
        unrealisedPnl = 0.0.toBigDecimal(),
        realisedPnl = 0.0.toBigDecimal(),
        entryPrice = 0.0.toBigDecimal(),
        breakEvenPrice = 0.0.toBigDecimal(),
        leverage = 0.0.toBigDecimal(),
        liqPrice = 0.0.toBigDecimal(),
        size = 0.0.toBigDecimal(),
        side = "BUY"
    )

    private var positionShort: Position = Position(
        pair = TradePair("TEST_PAIR"),
        marketPrice = 0.0.toBigDecimal(),
        unrealisedPnl = 0.0.toBigDecimal(),
        realisedPnl = 0.0.toBigDecimal(),
        entryPrice = 0.0.toBigDecimal(),
        breakEvenPrice = 0.0.toBigDecimal(),
        leverage = 0.0.toBigDecimal(),
        liqPrice = 0.0.toBigDecimal(),
        size = 0.0.toBigDecimal(),
        side = "SELL"
    )

    private var clientOrderId = 0

    private lateinit var candlestick: Candlestick

    fun emulate():TestBalance {

        val fileData = File("database/${params.botParams.pair}_klines.csv")

        val from = params.from?.let { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()) }
        val to = params.to?.let { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()) }

        fileData.forEachLine { line ->
            if (line.isNotBlank()) {
                candlestick = Candlestick(line.split(';'), 1.m())
                if (from == null || from.isBefore(candlestick.openTime.toZonedTime())) {
                    if (to == null || to.isAfter(candlestick.openTime.toZonedTime()))
                        handler(candlestick)
                }
            }
        }

        handler(BotEvent(type = BotEvent.Type.INTERRUPT))

        return profit
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> = TODO("not implemented")

    override fun getOpenOrders(pair: TradePair): List<Order> = orders.map { it.value }

    override fun getBalances(): Map<String, List<Balance>> = TODO("not implemented")

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = OrderBook(
        asks = listOf(Offer(price = lastBuyPrice, qty = BigDecimal(0))),
        bids = listOf(Offer(price = lastSellPrice, qty = BigDecimal(0)))
    )

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        mapOf("Total Balance" to getBalances()["Total Balance"]?.find { it.asset == asset })

    override fun getOrder(pair: TradePair, orderId: String): Order? = getOpenOrders(pair).find { it.orderId == orderId }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        TODO("Not yet implemented")

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> = TODO("Not yet implemented")

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        ++clientOrderId
        when (order.type) {
            TYPE.MARKET -> {
                when (order.side) {
                    SIDE.BUY -> {
                        val newSecondBalance = profit.secondBalance - (order.origQty * candlestick.high)
                        profit.secondBalance = newSecondBalance
                        profit.firstBalance += (order.origQty - order.origQty.percent(fee))

                        when (positionSide) {
                            DIRECTION.LONG -> positionLong.size += order.origQty
                            DIRECTION.SHORT -> positionShort.size -= order.origQty
                            else -> throw RuntimeException("Not supported DIRECTION")
                        }
                    }

                    SIDE.SELL -> {
                        val newFirstBalance = profit.firstBalance - order.origQty
                        profit.firstBalance = newFirstBalance
                        var profit = order.origQty * candlestick.low
                        profit = (profit - profit.percent(fee))

                        this.profit.secondBalance += profit

                        when (positionSide) {
                            DIRECTION.LONG -> positionLong.size -= order.origQty
                            DIRECTION.SHORT -> positionShort.size += order.origQty
                            else -> throw RuntimeException("Not supported DIRECTION")
                        }
                    }

                    else -> throw UnsupportedOrderSideException()
                }

                handler(positionLong)
                handler(positionShort)

                return order
            }

            TYPE.LIMIT -> {
                if (isStaticUpdate) updateStaticOrdersCount++
                ++clientOrderId
                val newOrder = Order(
                    pair = order.pair,
                    side = order.side,
                    type = order.type,
                    origQty = order.origQty,
                    price = order.price,
                    executedQty = BigDecimal(0),
                    status = STATUS.NEW,
                    orderId = clientOrderId.toString()
                )

                if (order.side == SIDE.SELL)
                    balance.firstBalance -= order.origQty
                else if (order.side == SIDE.BUY)
                    balance.secondBalance -= order.origQty * (order.price ?: 0.0.toBigDecimal())

                orders[newOrder.orderId] = newOrder

                return newOrder
            }

            else -> return order
        }
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        orders[orderId]?.let { order ->
            if (order.status != STATUS.NEW) return true
            order.status = STATUS.CANCELED

            if (order.side == SIDE.SELL)
                profit.firstBalance += order.origQty
            else if (order.side == SIDE.BUY)
                profit.secondBalance += order.origQty * (order.price ?: 0.0.toBigDecimal())
        } ?: log.info("Order: id = $orderId Not found!")
        return true
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamThreadStub()

    override fun getAllPairs(): List<TradePair> = TODO("not implemented")

    override fun close() = Unit
}