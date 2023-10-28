package bot.trade.exchanges.clients

import bot.trade.libs.UnsupportedClientException
import io.bitmax.api.rest.client.BitMaxRestApiClient
import io.bitmax.api.rest.client.BitMaxRestApiClientAccount
import mu.KotlinLogging

private val log = KotlinLogging.logger {}


fun newClient(exchangeEnum: ExchangeEnum, api: String? = null, sec: String? = null): Client =
    when (exchangeEnum) {
        ExchangeEnum.BYBIT -> ClientByBit(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.BINANCE -> ClientBinance(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.BITMAX -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.HUOBI -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.GATE -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.BINANCE_FUTURES -> ClientBinanceFutures(api, sec).also { log.info(" !!! Connect: $it !!! ") }
        ExchangeEnum.TEST -> ClientTestExchange()

        else -> throw UnsupportedClientException()
    }

fun newBitmaxClient(api: String? = null, sec: String? = null) =
    if (api != null && sec != null) BitMaxRestApiClientAccount(api, sec)
    else BitMaxRestApiClient()