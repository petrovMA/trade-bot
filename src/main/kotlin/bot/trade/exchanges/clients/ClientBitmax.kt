package bot.trade.exchanges.clients

import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.clients.stream.StreamBitmaxImpl
import bot.trade.exchanges.libs.bitmax.Product
import bot.trade.libs.*
import io.bitmax.api.Authorization
import utils.mapper.Mapper
import io.bitmax.api.rest.client.BitMaxRestApiClient
import io.bitmax.api.rest.client.BitmaxInterval
import io.bitmax.api.rest.messages.requests.RestCancelOrderRequest
import io.bitmax.api.rest.messages.requests.RestPlaceOrderRequest
import io.bitmax.api.rest.messages.responses.*
import io.bitmax.api.websocket.BitMaxApiWebSocketListener
import io.bitmax.api.websocket.messages.requests.WebSocketMsg
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue

class ClientBitmax(
    private val api: String? = null,
    private val sec: String? = null,
    private val instance: BitMaxRestApiClient = newBitmaxClient(api, sec)
) : Client {

    private val client = OkHttpClient()

    private var accountGroup: Int? = null; get() = field ?: run {
        field = instance.userInfo!!.data.accountGroup
        field
    }

    private val log = KotlinLogging.logger {}

    override fun getAllPairs(): List<TradePair> = client.newCall(
        Request.Builder()
            .url(instance.url + "api/v1/products")
            .get()
            .build()
    ).execute().let { resp ->
        try {

            Mapper.asObject<Array<Product>>(resp.body!!.string())
                .map { TradePair(it.baseAsset!!, it.quoteAsset!!) }
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Can't parse response:\ncode: ${resp.code}\nbody:\n${resp.body?.string()}")
            throw t
        }
    }

    override fun getOpenOrders(pair: TradePair): List<Order> = instance.openOrders!!.data
        .filter { it.symbol == "${pair.first}/${pair.second}" }
        .map { it.toOrder() }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> {
        val filter = pairs.map { "${it.first}/${it.second}" }
        return instance.openOrders!!.data
            .filter { filter.contains(it.symbol) }
            .map { it.toOrder() }
            .groupBy { TradePair(it.pair.toString()) }
    }

    override fun getBalances(): Map<String, List<Balance>> =
        mapOf("Total Balance" to instance.balance!!.data.map { it.toBalance() })

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook {
        val book = instance.getOrderBook("${pair.first}/${pair.second}")

        val asks: List<Offer> = book.data?.data?.asks?.map { Offer(it[0].toBigDecimal(), it[1].toBigDecimal()) }
            ?.sortedBy { it.price }
            ?.run { if (size > limit) subList(0, limit) else this }
            ?: throw UnsupportedOrderBookException("Order Book data Empty!")

        val bids: List<Offer> = book.data.data.bids.map { Offer(it[0].toBigDecimal(), it[1].toBigDecimal()) }
            .sortedBy { it.price }
            .run { if (size > limit) subList(size - limit, size) else this }

        return OrderBook(bids = bids, asks = asks)
    }

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        mapOf("Total Balance" to instance.balance!!.data.first { it.asset == asset }.toBalance())

    override fun getOrder(pair: TradePair, orderId: String): Order = instance.getOrder(orderId)
        ?.also { log.debug("instance.getOrder: {}", it) }?.data?.toOrder() ?: stub()

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        instance.getCandlestickBars("${pair.first}/${pair.second}", interval, countCandles)
            .map { it.toCandlestick() }

//        val result = client.newCall(
//            Request.Builder()
//                .url(
//                    instance.url + "api/v1/barhist?symbol=${"${pair.first}-${pair.second}"}&interval=${
//                        getInterval(
//                            interval
//                        )
//                    }&from=$from&to=$to"
//                )
//                .get()
//                .build()
//        ).execute()

//        return try {
//            Mapper.asObject(result.body!!.string(), Array<BitmaxCandlestick>::class.java)
//                .map { it.toCandlestick() }
//        } catch (t: Throwable) {
//            t.printStackTrace()
//            log.error("Can't parse response:\ncode: ${result.code}\nbody:\n${result.body?.string()}")
//            throw t
//        }
//    }

    override fun newOrder(order: Order, isStaticUpdate: Boolean, qty: String, price: String): Order {

        val formattedCount = java.lang.String.format(qty, order.origQty).replace(",", ".")
        val formattedPrice = java.lang.String.format(price, order.price).replace(",", ".")

        val time = System.currentTimeMillis()
        val placeCoid = "coid_$time"

        val orderRest = RestPlaceOrderRequest(
            time = time,
            symbol = "${order.pair.first}/${order.pair.second}",
            id = placeCoid,
            orderPrice = formattedPrice,
            orderQty = formattedCount,
            orderType = when (order.type) {
                TYPE.LIMIT -> "limit"
                TYPE.MARKET -> "market"
                else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${order.type}'!")
            },
            side = when (order.side) {
                SIDE.BUY -> "buy"
                SIDE.SELL -> "sell"
                else -> throw UnsupportedOrderSideException()
            }
        )

        log.debug("newOrder: {}", order)

        val resp = instance.placeOrder(orderRest)

        if (resp?.code == 0 && resp.data != null)
            return orderRest.toOrder(resp)
        else
            throw RuntimeException(
                "Error placeOrderResponse:" +
                        "\nCode = ${resp?.code}" +
                        "\nMessage = ${resp?.message}" +
                        "\nResponse = $resp"
            )
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        val time = System.currentTimeMillis()

        val cancelOrderRequest = RestCancelOrderRequest(
            time = time,
            symbol = "${pair.first}/${pair.second}",
            id = "coid_${time}",
            orderId = orderId
        )

        log.debug("CancelOrder: {}", cancelOrderRequest)

        val resp = instance.cancelOrder(cancelOrderRequest)

        if (resp?.data?.status == "Ack")
            return true
        else
            throw RuntimeException("Error placeOrderResponse: $resp")
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>): Stream {

        val url = "wss://ascendex.com/$accountGroup/api/pro/v1/stream"

        val book = getOrderBook(pair, 10)

        api ?: throw UnauthorizedException("Api-Key is NULL!")

        val authMsg = Authorization(api, sec).getAuthSocketMsg(System.currentTimeMillis())

        return StreamBitmaxImpl(
            pair = pair,
            client = BitMaxApiWebSocketListener(
                authMsg, url, 5000, true,
                WebSocketMsg(op = "sub", ch = "trades:${pair.first}/${pair.second}"),
                WebSocketMsg(op = "sub", ch = "depth:${pair.first}/${pair.second}"),
                WebSocketMsg(op = "sub", ch = "order:cash")
            ),
            interval = interval,
            queue = queue,
            asks = book.asks.associate { it.price to it.qty }.toMutableMap(),
            bids = book.bids.associate { it.price to it.qty }.toMutableMap()
        )
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun RestBalance.toBalance(): Balance =
        Balance(
            asset,
            totalBalance.toBigDecimal(),
            availableBalance.toBigDecimal(),
            totalBalance.toBigDecimal() - availableBalance.toBigDecimal()
        )


    private fun RestBarHist.toCandlestick(): Candlestick = Candlestick(
        openTime = data!!.time,
        volume = data!!.volume!!.toBigDecimal(),
        close = data!!.close!!.toBigDecimal(),
        high = data!!.high!!.toBigDecimal(),
        low = data!!.low!!.toBigDecimal(),
        open = data!!.open!!.toBigDecimal(),
        closeTime = data!!.time + when (BitmaxInterval.from(data!!.interval!!)) {
            BitmaxInterval.ONE_MINUTE -> 60_000L - 1
            BitmaxInterval.FIVE_MINUTES -> 300_000L - 1
            BitmaxInterval.FIFTEEN_MINUTES -> 900_000L - 1
            BitmaxInterval.HALF_HOURLY -> 1_800_000L - 1
            BitmaxInterval.HOURLY -> 3_600_000L - 1
            BitmaxInterval.TWO_HOURLY -> 3_600_000L * 2 - 1
            BitmaxInterval.FOUR_HOURLY -> 3_600_000L * 4 - 1
            BitmaxInterval.SIX_HOURLY -> 3_600_000L * 6 - 1
            BitmaxInterval.TWELVE_HOURLY -> 3_600_000L * 12 - 1
            BitmaxInterval.DAILY -> 3_600_000L * 24 - 1
            BitmaxInterval.WEEKLY -> 3_600_000L * 7 - 1
            BitmaxInterval.MONTHLY -> 3_600_000L * 30 - 1
        }
    )

    private fun RestPlaceOrderRequest.toOrder(resp: PlaceOrCancelOrder): Order = Order(
        orderId = resp.data!!.info.orderId,
        pair = TradePair(symbol.replace('/', '_')),
        price = orderPrice.toBigDecimal(),
        origQty = orderQty.toBigDecimal(),
        executedQty = BigDecimal(0),
        side = when (side) {
            "buy" -> SIDE.BUY
            "sell" -> SIDE.SELL
            else -> SIDE.UNSUPPORTED
        },
        type = when (orderType) {
            "limit" -> TYPE.LIMIT
            "market" -> TYPE.MARKET
            else -> TYPE.UNSUPPORTED
        },
        status = STATUS.NEW
    )

    private fun RestOrderDetailsData.toOrder(): Order = Order(
        orderId = orderId,
        pair = TradePair(symbol.replace('/', '_')),
        price = price.toBigDecimal(),
        origQty = orderQty.toBigDecimal(),
        executedQty = cumFilledQty.toBigDecimal(),
        side = when (side) {
            "Buy" -> SIDE.BUY
            "Sell" -> SIDE.SELL
            else -> SIDE.UNSUPPORTED
        },
        type = when (orderType) {
            "Limit" -> TYPE.LIMIT
            "Market" -> TYPE.MARKET
            else -> TYPE.UNSUPPORTED
        },
        status = when (status) {
            "PendingNew" -> STATUS.NEW
            "New" -> STATUS.NEW
            "PartiallyFilled" -> STATUS.PARTIALLY_FILLED
            "Filled" -> STATUS.FILLED
            "Canceled" -> STATUS.CANCELED
            "Rejected" -> STATUS.REJECTED
            else -> STATUS.UNSUPPORTED
        }
    )

    override fun toString(): String = "BITMAX"
}