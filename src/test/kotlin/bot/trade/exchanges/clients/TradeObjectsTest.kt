package bot.trade.exchanges.clients

import io.bybit.api.rest.response.PositionResponse.Result
import io.bybit.api.websocket.messages.response.Position.Data
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TradeObjectsTest {

    @Test
    fun positionConstructor() {
        val position1 = Position(
            Result.Position(
                symbol = "ETHUSDT",
                markPrice = "1998",
                unrealisedPnl = "0",
                curRealisedPnl = "8",
                avgPrice = "2000",
                leverage = "10",
                liqPrice = "0",
                size = "0.02",
                side = "buy",

                adlRankIndicator = 0,
                autoAddMargin = 0,
                bustPrice = "0",
                createdTime = "0",
                cumRealisedPnl = "0",
                isReduceOnly = false,
                leverageSysUpdatedTime = "0",
                mmrSysUpdateTime = "0",
                positionBalance = "0",
                positionIM = "0",
                positionIdx = 0,
                positionMM = "0",
                positionStatus = "0",
                positionValue = "0",
                riskId = 0,
                riskLimitValue = "0",
                seq = 0,
                stopLoss = "0",
                takeProfit = "0",
                tpslMode = "0",
                tradeMode = 0,
                trailingStop = "0",
                updatedTime = "0"
            )
        )

        assertEquals(
            Position(
                pair = TradePair("ETH_USDT"),
                marketPrice = "1998".toBigDecimal(),
                unrealisedPnl = "0".toBigDecimal(),
                realisedPnl = "8".toBigDecimal(),
                entryPrice = "2000".toBigDecimal(),
                breakEvenPrice = "1600.00".toBigDecimal(),
                leverage = "10".toBigDecimal(),
                liqPrice = "0".toBigDecimal(),
                size = "0.02".toBigDecimal(),
                side = "buy"
            ),
            position1
        )

        val position2 = Position(
            Data(
                symbol = "ETHUSDT",
                markPrice = "1998",
                unrealisedPnl = "0",
                curRealisedPnl = "9",
                entryPrice = "2000",
                leverage = "10",
                liqPrice = "0",
                size = "0.02",
                side = "sell",

                adlRankIndicator = 0,
                autoAddMargin = 0,
                bustPrice = "0",
                createdTime = "0",
                category = "0",
                cumRealisedPnl = "0",
                isReduceOnly = false,
                leverageSysUpdatedTime = "0",
                mmrSysUpdatedTime = "0",
                positionBalance = "0",
                positionIM = "0",
                positionIdx = 0,
                positionMM = "0",
                positionStatus = "0",
                positionValue = "0",
                riskId = 0,
                riskLimitValue = "0",
                seq = 0,
                stopLoss = "0",
                takeProfit = "0",
                tpslMode = "0",
                tradeMode = 0,
                trailingStop = "0",
                updatedTime = "0"
            )
        )

        assertEquals(
            Position(
                pair = TradePair("ETH_USDT"),
                marketPrice = "1998".toBigDecimal(),
                unrealisedPnl = "0".toBigDecimal(),
                realisedPnl = "9".toBigDecimal(),
                entryPrice = "2000".toBigDecimal(),
                breakEvenPrice = "2450.00".toBigDecimal(),
                leverage = "10".toBigDecimal(),
                liqPrice = "0".toBigDecimal(),
                size = "0.02".toBigDecimal(),
                side = "sell"
            ),
            position2
        )
    }

    @Test
    fun tradePairTest() {
        assertEquals(TradePair("MANA", "USDT"), TradePair("MANAUSDT"))
        assertEquals(TradePair("POLYGON", "ETH"), TradePair("POLYGONETH"))
        assertEquals(TradePair("ZK", "USDC"), TradePair("ZKUSDC"))
        assertEquals(TradePair("ETH", "BTC"), TradePair("ETHBTC"))
        Assertions.assertThrows(RuntimeException::class.java) { TradePair("TUSDDAI") }
    }
}