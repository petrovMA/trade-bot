package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.libs.UnsupportedClientException
import io.bitmax.api.rest.client.BitMaxRestApiClient
import io.bitmax.api.rest.client.BitMaxRestApiClientAccount
import mu.KotlinLogging
private val log = KotlinLogging.logger {}


fun newClient(exchangeEnum: ExchangeEnum, api: String? = null, sec: String? = null): Client =
        when (exchangeEnum) {
            ExchangeEnum.BINANCE -> BinanceClient(api, sec).also { log.info(" !!! Connect !!! ") }
            ExchangeEnum.BITMAX -> BitmaxClient(api, sec).also { log.info(" !!! Connect !!! ") }
            else -> throw UnsupportedClientException()
        }

fun newBitmaxClient(api: String? = null, sec: String? = null) =
        if (api != null && sec != null) BitMaxRestApiClientAccount(api, sec)
        else BitMaxRestApiClient()