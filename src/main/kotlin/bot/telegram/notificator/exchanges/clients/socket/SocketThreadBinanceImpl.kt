//package bot.telegram.notificator.exchange.clients.socket
//
//import bot.telegram.notificator.exchange.clients.Candlestick
//import bot.telegram.notificator.exchange.clients.CommonExchangeData
//import bot.telegram.notificator.exchange.clients.DepthEventOrders
//import bot.telegram.notificator.exchange.clients.OrderEntry
//import com.binance.api.client.BinanceApiClientFactory
//import com.binance.api.client.BinanceApiWebSocketClient
//import com.binance.api.client.domain.event.CandlestickEvent
//import com.binance.api.client.domain.event.DepthEvent
//import com.binance.api.client.domain.event.TradeEvent
//import com.binance.api.client.domain.market.CandlestickInterval
//import com.binance.api.client.domain.market.DepthInterval
//import org.apache.log4j.Logger
//
//import java.util.concurrent.BlockingQueue
//
//class SocketThreadBinanceImpl(
//        val symbol: String,
//        var client: BinanceApiWebSocketClient?,
//        val interval: CandlestickInterval,
//        val queue: BlockingQueue<CommonExchangeData>)
//    : SocketThread() {
//
//    override fun run() {
//        try {
//            client!!.onTradeEvent(symbol) { response: TradeEvent ->
//                queue.add(OrderEntry(price = response.price.toDouble(), qty = response.quantity))
//            }
//        } catch (e: Exception) {
//            LOGGER.error("Socket $symbol connection Exception: ", e)
//            e.printStackTrace()
//            client = BinanceApiClientFactory.newInstance().newWebSocketClient()
//        }
//
//        try {
//            client!!.onCandlestickEvent(symbol, interval) { response: CandlestickEvent ->
//                if (response.barFinal!!)
//                    queue.add(toCandlestick(response))
//            }
//        } catch (e: Exception) {
//            LOGGER.error("Socket $symbol connection Exception: ", e)
//            e.printStackTrace()
//            client = BinanceApiClientFactory.newInstance().newWebSocketClient()
//        }
//
//        try {
//            client!!.onDepthEvent(symbol, DepthInterval.FIVE) { response: DepthEvent ->
//                val bid = response.bids.first()
//                val ask = response.asks.first()
//                queue.add(
//                    DepthEventOrders(
//                        bid = OrderEntry(bid.price.toDouble(), bid.qty.toDouble()),
//                        ask = OrderEntry(ask.price.toDouble(), ask.qty.toDouble())
//                )
//                )
//            }
//        } catch (e: Exception) {
//            LOGGER.error("Socket $symbol connection Exception: ", e)
//            e.printStackTrace()
//            client = BinanceApiClientFactory.newInstance().newWebSocketClient()
//
//        }
//    }
//
//    companion object {
//        private val LOGGER = Logger.getLogger(SocketThreadBinanceImpl::class.java)
//    }
//
//    private fun toCandlestick(event: CandlestickEvent): Candlestick = Candlestick(
//            openTime = event.openTime,
//            closeTime = event.closeTime,
//            open = event.open.toDouble(),
//            high = event.high.toDouble(),
//            low = event.low.toDouble(),
//            close = event.close.toDouble(),
//            volume = event.volume.toDouble()
//    )
//}
