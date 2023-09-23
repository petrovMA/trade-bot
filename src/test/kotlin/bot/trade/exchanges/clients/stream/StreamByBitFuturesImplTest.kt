package bot.trade.exchanges.clients.stream

import bot.trade.exchanges.clients.*
import org.junit.jupiter.api.Test
import utils.mapper.Mapper
import utils.resourceFile
import java.math.BigDecimal

class StreamByBitFuturesImplTest {

    @Test
    fun checkOrderDeserialize() {
        val marketOrderMessage = resourceFile<StreamByBitFuturesImplTest>("market_order.json").readText()
        val limitOrderMessage = resourceFile<StreamByBitFuturesImplTest>("limit_order.json").readText()

        val byBitMarketOrder = Mapper.asObject<io.bybit.api.websocket.messages.response.Order>(marketOrderMessage)
        val byBitLimitOrder = Mapper.asObject<io.bybit.api.websocket.messages.response.Order>(limitOrderMessage)

        assert(byBitMarketOrder.data.size == 1)
        assert(byBitLimitOrder.data.size == 1)

        val marketOrder = Order(byBitMarketOrder.data[0])
        val limitOrder = Order(byBitLimitOrder.data[0])

        marketOrder.run {
            assert(orderId == "b6f820be-a270-49fe-b9c9-7515cff3e6e5")
            assert(pair == TradePair("ETH_USDT"))
            assert(price == BigDecimal("1590.21"))
            assert(origQty == BigDecimal("0.011"))
            assert(executedQty == BigDecimal("0.01"))
            assert(side == SIDE.BUY)
            assert(type == TYPE.MARKET)
            assert(status == STATUS.FILLED)
            assert(stopPrice == null)
            assert(lastBorderPrice == null)
            assert(fee == BigDecimal("0.00874616"))
        }

        limitOrder.run {
            assert(orderId == "0f8e9e9e-9656-4834-8f5a-fc06f3a1469d")
            assert(pair == TradePair("ETH_USDT"))
            assert(price == BigDecimal("1570.21"))
            assert(origQty == BigDecimal("0.01"))
            assert(executedQty == BigDecimal("0"))
            assert(side == SIDE.BUY)
            assert(type == TYPE.LIMIT)
            assert(status == STATUS.NEW)
            assert(stopPrice == null)
            assert(lastBorderPrice == null)
            assert(fee == BigDecimal("0"))
        }
    }
}