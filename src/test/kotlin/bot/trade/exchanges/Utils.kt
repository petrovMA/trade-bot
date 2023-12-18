package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.json
import bot.trade.libs.readConf
import com.google.gson.reflect.TypeToken
import utils.mapper.Mapper
import utils.resourceFile
import java.io.File
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque

fun assertOrders(expected: Map<String, Order>, actual: Map<String, Order>) {
    assert(expected.size == actual.size) { "expected.size != actual.size" }
    expected.forEach { (k, v) ->
        assert(actual.containsKey(k)) { "actual not contains key $k" }
        assert(v == actual[k]) { "expected[$k] != actual[$k]" }
    }
}

fun assertOrders(expected: List<Order>, actual: List<Order>) {
    assert(expected.size == actual.size) { "expected.size != actual.size, (${expected.size} != ${actual.size})" }
    expected.forEachIndexed { index, v ->
        assert(v == actual[index]) { "Expected:\n${json(v)}\n\nActual:\n${json(actual[index])}" }
    }
}

fun assertOrders(expectedFile: File?, actual: Map<String, Order>, messagePredicate: String = "") {

    val expected = expectedFile
        ?.let { Mapper.asMapObjects<String, Order>(it, object : TypeToken<Map<String?, Order?>?>() {}.type) }
        ?: mapOf()

    assert(expected.size == actual.size) { "${messagePredicate}expected.size != actual.size, (${expected.size} != ${actual.size})" }
    expected.forEach { (k, v) ->
        assert(actual.containsKey(k)) { "${messagePredicate}actual not contains key $k" }
        assert(v == actual[k]) { "${messagePredicate}[$k] not equals,\nExpected:\n${json(v)}\n\nActual:\n${json(actual[k]!!)}" }
    }
}



fun testExchange(settingsFile: String, endTime: Long? = null) = ClientTestExchange().let { exchange ->
    AlgorithmTrader(
        botSettings = Mapper.asObject<BotSettingsTrader>(settingsFile.file().readText()),
        exchangeBotsFiles = "",
        queue = LinkedBlockingDeque<CommonExchangeData>(),
        exchangeEnum = ExchangeEnum.TEST,
        conf = readConf("TEST.conf".file().path)!!,
        api = "",
        sec = "",
        client = exchange,
        isLog = false,
        isEmulate = true,
        endTimeForTrendCalculator = endTime,
    ) { _, _ -> } to exchange
}

fun String.file() = resourceFile<AlgorithmTraderTest>(this)

infix fun Int.startExclusive(other: Int): IntRange = IntRange(this + 1, other)

fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)