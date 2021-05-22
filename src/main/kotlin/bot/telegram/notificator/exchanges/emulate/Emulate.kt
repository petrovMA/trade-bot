package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.CandlestickListsIterator
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.emulate.libs.writeIntoExcel
import bot.telegram.notificator.exchanges.TraderAlgorithm
import bot.telegram.notificator.exchanges.connect
import bot.telegram.notificator.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.text.toDouble

class Emulate(
    val sendFile: (File) -> Unit,
    val sendMessage: (String) -> Unit,
    private val pair: String,
    private val startDate: String,
    private val endDate: String,
    candlestickDataPath: Map<ExchangeEnum, String>,
    exchangeEnum: ExchangeEnum,
    private val isFindParam: Boolean = true
) : Thread() {

    private val log = KotlinLogging.logger {}
    private val conf = readConf("exchange/emulate/exchange.conf")
        ?: throw ConfigNotFoundException("Config file not found in: 'exchange/emulate/exchange.conf'")

    private val emulateDataPath = candlestickDataPath.getValue(exchangeEnum)

    override fun run() {
        try {
            val result = if (isFindParam) emulate(pair.toRegex(), startDate, endDate, emulateDataPath)
            else findParams(TradePair(pair), startDate, endDate, emulateDataPath)
            sendFile(result)
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error { "Emulate error:\n$t" }
            sendMessage("Emulate error:\n${printTrace(t)}")
        }
    }

    private fun emulate(regex: Regex, startDate: String, endDate: String, pathDB: String): File {

        val logging = conf.getBoolean("logging")

        val connect = connect(pathDB, log)

        var result: List<EmulateResult>

        try {
            val stmt = connect.createStatement()
            val resultSetTables = stmt.executeQuery("SELECT name FROM sqlite_master where type='table'")

            val tablesPairs = ArrayList<Pair<String, TradePair>>()

            while (resultSetTables.next()) {
                val tableName: String = resultSetTables.getString("name")
                val pair = tableName.substring(12)
                if (pair.matches(regex))
                    tablesPairs.add(tableName to TradePair(pair))
            }
            stmt.close()

            result = tablesPairs.asSequence().map { tablePair ->

                calcProfit(TestClient(
                    iterator = CandlestickListsIterator(
                        connect,
                        tableName = tablePair.first,
                        listSize = 500,
                        startDateTime = Timestamp.valueOf("${startDate.replace('_', '-')} 00:00:00.000000"),
                        endDateTime = Timestamp.valueOf("${endDate.replace('_', '-')} 00:00:00.000000"),
                        interval = INTERVAL.FIVE_MINUTES,
                        fillGaps = true
                    ),
                    balance = TestBalance(
                        secondBalance = BigDecimal(100),
                        balanceTrade = BigDecimal(50),
                        tradePair = tablePair.second
                    ),
                    startCandleNum = (conf.getInt("interval.candles_buy") to conf.getInt("interval.candles_sell"))
                        .run { if (first > second) first else second }
                ),
                    logging = logging
                )
            }
                .filterNotNull()
                .sortedBy { it.profitByFirstPrice * -1 }
                .toList()

        } catch (t: Throwable) {
            log.error("CalcProfit ERROR:", t)
            sendMessage("Calc profit ERROR:\n${printTrace(t, 5)}")
            result = emptyList()
        } finally {
            connect.close()
        }

        val resultDir = File("emulate/results")
        if (!resultDir.isDirectory)
            resultDir.mkdirs()

        return writeIntoExcel(
            file = File(
                "exchange/emulate/results/start_${startDate}___end_${endDate}___time_" +
                        "${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}.xls"
            ),
            head = listOf(
                "pair",
                "Profit\nByLast\nPrice",
                "Profit\nByFirst\nPrice",
                "Execute\norder\ncount",
                "Update\nstatic\norder\ncount",
                "first\nBalance",
                "second\nBalance",
                "second\nBalance\nByFirst\nPrice",
                "second\nBalance\nByLast\nPrice",
                "from",
                "to"
            ),
            lines = result
        )
    }

    private fun findParams(pair: TradePair, startDate: String, endDate: String, pathDB: String): File {

        val logging = conf.getBoolean("logging")

        val connect = connect(pathDB, log)

        var result: List<EmulateResult>

        try {

            val func: (Double, Double, Int, Int, Double) -> EmulateResult? =
                { buyProf: Double, sellProf: Double, candlesBuy: Int, candlesSell: Int, updStaticOrders: Double ->
                    calcProfit(
                        buyProf = buyProf,
                        sellProf = sellProf,
                        candlesBuy = candlesBuy,
                        candlesSell = candlesSell,
                        updStaticOrders = updStaticOrders,
                        client = TestClient(
                            iterator = CandlestickListsIterator(
                                connect,
                                tableName = "CANDLESTICK_$pair",
                                listSize = 500,
                                startDateTime = Timestamp.valueOf(
                                    "${startDate.replace('_', '-')} 00:00:00.000000"
                                ),
                                endDateTime = Timestamp.valueOf(
                                    "${endDate.replace('_', '-')} 00:00:00.000000"
                                ),
                                interval = INTERVAL.FIVE_MINUTES,
                                fillGaps = true
                            ),
                            balance = TestBalance(
                                secondBalance = BigDecimal(100),
                                balanceTrade = BigDecimal(50),
                                tradePair = pair
                            ),
                            startCandleNum = (conf.getInt("interval.candles_buy") to conf.getInt("interval.candles_sell"))
                                .run { if (first > second) first else second }
                        ),
                        logging = logging

                    )
                }

            val buyProfStart = 2.0
            val sellProfStart = 2.0
            val staticOrdersStart = 3.0
            val candlesBuyStart = 15
            val candlesSellStart = 15

            var staticOrders = staticOrdersStart
            var buyProf = buyProfStart
            var sellProf = sellProfStart
            var candlesBuy = candlesBuyStart
            var candlesSell = candlesSellStart
            val calculatedProf = ArrayList<EmulateResult?>()

            while (buyProf <= 4.0) {
                while (sellProf <= 4.0) {
                    while (candlesBuy <= 30 && candlesSell <= 30) {
                        while (staticOrders <= 8.0) {

                            if (buyProf < staticOrders && sellProf < staticOrders)
                                calculatedProf.add(func(buyProf, sellProf, candlesBuy, candlesSell, staticOrders))

                            staticOrders += 0.2
                        }

                        staticOrders = staticOrdersStart
                        candlesBuy += 5
                        candlesSell += 5
                    }

                    staticOrders = staticOrdersStart
                    candlesBuy = candlesBuyStart
                    candlesSell = candlesSellStart
                    sellProf += 0.2

                    log.info { "buyProf=$buyProf\t sellProf=$sellProf" }
                }

                staticOrders = staticOrdersStart
                candlesBuy = candlesBuyStart
                candlesSell = candlesSellStart
                sellProf = sellProfStart
                buyProf += 0.2

                log.info { "$buyProf $sellProf   list_size = ${calculatedProf.size}" }
            }


            result = calculatedProf
                .filterNotNull()
                .sortedBy { it.profitByFirstPrice * -1 }
                .toList()

        } catch (t: Throwable) {
            log.error("CalcProfit ERROR:", t)
            sendMessage("Calc profit ERROR:\n${printTrace(t, 5)}")
            result = emptyList()
        } finally {
            connect.close()
        }

        val resultDir = File("emulate/results")
        if (!resultDir.isDirectory)
            resultDir.mkdirs()

        return writeIntoExcel(
            file = File(
                "exchange/emulate/results/${pair}_start_${startDate}___end_${endDate}___time_" +
                        "${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}.xls"
            ),
            head = listOf(
                "pair",
                "Profit\nByLast\nPrice",
                "Profit\nByFirst\nPrice",
                "Execute\norder\ncount",
                "Update\nstatic\norder\ncount",
                "first\nBalance",
                "second\nBalance",
                "second\nBalance\nByFirst\nPrice",
                "second\nBalance\nByLast\nPrice",
                "from",
                "to",
                "",
                "buy_prof",
                "sell_prof",
                "buy_candles",
                "sell_candles",
                "upd_static\norders"
            ),
            lines = result
        )
    }

    private fun calcProfit(
        client: TestClient,
        logging: Boolean,
        buyProf: Double? = null,
        sellProf: Double? = null,
        candlesBuy: Int? = null,
        candlesSell: Int? = null,
        updStaticOrders: Double? = null
    ): EmulateResult? {
        try {
            val trade = TraderAlgorithm(
                conf = conf,
                queue = client.queue,
                firstSymbol = client.balance.tradePair.first,
                secondSymbol = client.balance.tradePair.second,
                balanceTrade = client.balance.balanceTrade,
                exchangeEnum = ExchangeEnum.STUB_TEST,
                client = client,
                path = "exchange/emulate/data/results/${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}",
                syncTimeInterval = (-1000).ms(),
                isLog = logging,
                isEmulate = true,
                percentBuyProf = buyProf?.toBigDecimal(),
                percentSellProf = sellProf?.toBigDecimal(),
                intervalCandlesBuy = candlesBuy,
                intervalCandlesSell = candlesSell,
                updStaticOrders = updStaticOrders?.toBigDecimal()
            ) { }

            trade.start()
            trade.join()

            File("exchange/emulate/data/results").delete()

            val firstBalanceA = client.balance.firstBalance.let {
                client.balance.firstBalance + client.getOpenOrders(
                    TradePair(
                        client.balance.tradePair.first,
                        client.balance.tradePair.second
                    )
                ).map { order ->
                    if (order.status == STATUS.NEW && order.side == SIDE.SELL) {
                        order.origQty
                    } else BigDecimal(0)
                }.fold(BigDecimal.ZERO, BigDecimal::add)
            }

            val secondBalanceA = client.balance.secondBalance.let {
                client.balance.secondBalance + client.getOpenOrders(
                    TradePair(
                        client.balance.tradePair.first,
                        client.balance.tradePair.second
                    )
                ).map { order ->
                    if (order.status == STATUS.NEW && order.side == SIDE.BUY) {
                        order.origQty * order.price
                    } else BigDecimal(0)
                }.fold(BigDecimal.ZERO, BigDecimal::add)
            }

            val secondBalanceByFirstPrice: BigDecimal = secondBalanceA + (firstBalanceA * client.firstPrice)
            val secondBalanceByLastPrice: BigDecimal = secondBalanceA + (firstBalanceA * client.lastSellPrice)

            val allStartSecondBalance = (client.startFirstBalance * client.firstPrice) + client.startSecondBalance

            var result: EmulateResult? = null
            try {
                result = EmulateResult(
                    pair = client.balance.tradePair.toString(),
                    profitByLastPrice = String.format("%.4f", secondBalanceByLastPrice - allStartSecondBalance)
                        .toDouble(),
                    profitByFirstPrice = String.format("%.4f", secondBalanceByFirstPrice - allStartSecondBalance)
                        .toDouble(),
                    executeOrderCount = client.executedOrdersCount.toDouble(),
                    updateStaticOrderCount = client.updateStaticOrdersCount.toDouble(),
                    firstBalance = String.format("%.4f", firstBalanceA).toDouble(),
                    secondBalance = String.format("%.4f", secondBalanceA).toDouble(),
                    secondBalanceByFirstPrice = String.format("%.4f", secondBalanceByFirstPrice).toDouble(),
                    secondBalanceByLastPrice = String.format("%.4f", secondBalanceByLastPrice).toDouble(),
                    from = convertTime(trade.firstCandlestick?.openTime ?: 0).removePrefix("20"),
                    to = convertTime(trade.lastCandlestick?.openTime ?: 0).removePrefix("20"),
                    buyProfitPercent = buyProf,
                    sellProfitPercent = sellProf,
                    candlesBuyInterval = candlesBuy?.toDouble(),
                    candlesSellInterval = candlesSell?.toDouble(),
                    updStaticOrders = updStaticOrders
                )

            } catch (t: Throwable) {
                t.printStackTrace()
                log.error("Emulate error: ", t)
            }

            return result
        } catch (t: Throwable) {
            log.error("Emulate Error:", t)
            return null
        }
    }

    data class EmulateResult(
        val pair: String,
        val profitByLastPrice: Double,
        val profitByFirstPrice: Double,
        val executeOrderCount: Double,
        val updateStaticOrderCount: Double,
        val firstBalance: Double,
        val secondBalance: Double,
        val secondBalanceByFirstPrice: Double,
        val secondBalanceByLastPrice: Double,
        val from: String,
        val to: String,
        val buyProfitPercent: Double? = null,
        val sellProfitPercent: Double? = null,
        val candlesBuyInterval: Double? = null,
        val candlesSellInterval: Double? = null,
        val updStaticOrders: Double? = null
    )
}