package bot.trade.exchanges

import bot.trade.exchanges.clients.Order
import bot.trade.libs.json
import com.google.gson.reflect.TypeToken
import utils.mapper.Mapper
import java.io.File

fun assertOrders(expected: Map<String, Order>, actual: Map<String, Order>) {
    assert(expected.size == actual.size) { "expected.size != actual.size" }
    expected.forEach { (k, v) ->
        assert(actual.containsKey(k)) { "actual not contains key $k" }
        assert(v == actual[k]) { "expected[$k] != actual[$k]" }
    }
}

fun assertOrders(expected: List<Order>, actual: List<Order>) {
    assert(expected.size == actual.size) { "expected.size != actual.size" }
    expected.forEachIndexed { index, v ->
        assert(v == actual[index]) { "Expected:\n${json(v)}\n\nActual:\n${json(actual[index])}" }
    }
}

fun assertOrders(expectedFile: File, actual: Map<String, Order>) {
    val expected = Mapper.asMapObjects<String, Order>(expectedFile, object : TypeToken<Map<String?, Order?>?>() {}.type)
    assert(expected.size == actual.size) { "expected.size != actual.size" }
    expected.forEach { (k, v) ->
        assert(actual.containsKey(k)) { "actual not contains key $k" }
        assert(v == actual[k]) { "[$k] not equals,\nExpected:\n${json(v)}\n\nActual:\n${json(actual[k]!!)}" }
    }
}