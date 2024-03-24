package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.StreamThreadStub
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class TestClientFileData(
    val handler: (CommonExchangeData?) -> Unit,
    val balance: TestBalance,
    startCandleNum: Int,
    private val fee: BigDecimal = BigDecimal(0.1)
) : Client {
    private val log = KotlinLogging.logger {}
    private var orders: MutableMap<String, Order> = HashMap()
    var lastSellPrice: BigDecimal = BigDecimal(0)
        private set
    private var lastBuyPrice: BigDecimal = BigDecimal(0)

    private var clientOrderId = 0

    var updateStaticOrdersCount: Int = 0
        private set
    var executedOrdersCount: Int = 0
        private set

    private lateinit var candlestick: Candlestick

    fun emulate(fileData: File) {
        fileData.forEachLine { line ->
            candlestick = Candlestick(line.split(';'), 1.m())
            handler(candlestick)
        }
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> =TODO("not implemented")

    override fun getOpenOrders(pair: TradePair): List<Order> = orders.map { it.value }

    override fun getBalances(): Map<String, List<Balance>> = mapOf(
        "Total Balance" to listOf(
            Balance(
                asset = balance.tradePair.first,
                free = balance.firstBalance,
                total = balance.firstBalance,
                locked = BigDecimal(0)
            ),
            Balance(
                asset = balance.tradePair.second,
                free = balance.secondBalance,
                total = balance.secondBalance,
                locked = BigDecimal(0)
            )
        )
    )

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
        when (order.type) {
            TYPE.MARKET -> {
                when (order.side) {
                    SIDE.BUY -> {
                        val newSecondBalance = balance.secondBalance - (order.origQty * candlestick.high)
                        if (BigDecimal.ZERO <= newSecondBalance) {
                            balance.secondBalance = newSecondBalance
                            executedOrdersCount++
                            balance.firstBalance += (order.origQty - order.origQty.percent(fee))
                        } else throw NotEnoughBalanceException("Account has insufficient balance for requested action.")
                    }

                    SIDE.SELL -> {
                        val newFirstBalance = balance.firstBalance - order.origQty
                        if (BigDecimal.ZERO <= newFirstBalance) {
                            balance.firstBalance = newFirstBalance
                            executedOrdersCount++
                            var profit = order.origQty * candlestick.low
                            profit = (profit - profit.percent(fee))

                            balance.secondBalance += profit
                        } else throw NotEnoughBalanceException("Account has insufficient balance for requested action.")
                    }

                    else -> throw UnsupportedOrderSideException()
                }
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
                balance.firstBalance += order.origQty
            else if (order.side == SIDE.BUY)
                balance.secondBalance += order.origQty * (order.price ?: 0.0.toBigDecimal())
        } ?: log.info("Order: id = $orderId Not found!")
        return true
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamThreadStub()

    override fun getAllPairs(): List<TradePair> = TODO("not implemented")

    override fun close() = Unit
}