package bot.trade.exchanges

import bot.trade.exchanges.clients.*
import bot.trade.libs.readConf
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import utils.mapper.Mapper
import utils.resourceFile
import java.util.concurrent.LinkedBlockingDeque

class AlgorithmTraderTest {

    @Test
    fun testExecuteInOrdersWithMinOrderAmount() {
        val algorithmTrader = AlgorithmTrader(
            botSettings = Mapper.asObject<BotSettingsTrader>(resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountSettings.json").readText()),
            exchangeBotsFiles = "",
            queue = LinkedBlockingDeque<CommonExchangeData>(),
            exchangeEnum = ExchangeEnum.TEST,
            conf = readConf(resourceFile<AlgorithmTraderTest>("TEST.conf").path)!!,
            api = "",
            sec = "",
            client = newClient(ExchangeEnum.TEST, "", ""),
            isLog = false,
            isEmulate = true,
        ) { _, _ -> }

        if (algorithmTrader.client !is ClientTestExchange)
            fail("algorithmTrader.client is Not ClientTestExchange")

        algorithmTrader.orders.clear()

        Mapper.asMapObjects<String, Order>(
            resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountOrders.json"),
            object : TypeToken<Map<String?, Order?>?>() {}.type
        ).forEach { (k, v) -> algorithmTrader.orders[k] = v }

        assert(
            algorithmTrader.orders == Mapper.asMapObjects<String, Order>(
                resourceFile<AlgorithmTraderTest>("testExecuteInOrdersWithMinOrderAmountOrders.json"),
                object : TypeToken<Map<String?, Order?>?>() {}.type
            )
        )
    }
}