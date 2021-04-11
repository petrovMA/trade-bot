package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.libs.UnsupportedClientException
import io.bitmax.api.rest.client.BitMaxRestApiClient
import io.bitmax.api.rest.client.BitMaxRestApiClientAccount
import mu.KotlinLogging
private val log = KotlinLogging.logger {}


fun newClient(exchangeEnum: ExchangeEnum, api: String? = null, sec: String? = null): Client =
        when (exchangeEnum) {
            ExchangeEnum.BINANCE -> ClientBinance(api, sec).also { log.info(" !!! Connect: $it !!! ") }
            ExchangeEnum.BITMAX -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
            ExchangeEnum.HUOBI -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
            else -> throw UnsupportedClientException()
        }

fun newBitmaxClient(api: String? = null, sec: String? = null) =
        if (api != null && sec != null) BitMaxRestApiClientAccount(api, sec)
        else BitMaxRestApiClient()