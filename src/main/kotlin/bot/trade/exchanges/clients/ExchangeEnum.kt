package bot.trade.exchanges.clients

import bot.trade.libs.UnsupportedClientException
import bot.trade.libs.readConf

enum class ExchangeEnum {
    BYBIT_SPOT,
    BYBIT_FUTURES,
    BYBIT,
    BINANCE,
    BINANCE_FUTURES,
    BITMAX,
    HUOBI,
    GATE,
    MEXC,
    ONEINCH,
    EXTENDED,
    STUB_TEST,
    TEST;

    companion object {

        // TODO:: IMPLEMENT ONE CLIENT INSTANCE FOR EVERY EXCHANGE, DON'T CREATE NEW CLIENT FOR EVERY ACTION!!!
        fun ExchangeEnum.newClient(api: String? = null, sec: String? = null): Client =
            when (this) {
                BYBIT_SPOT -> ClientByBitSpot(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                BYBIT_FUTURES -> ClientByBitFutures(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                BYBIT -> ClientByBitSpot(api, sec).also {
                    log.warn("BYBIT is deprecated, use BYBIT_SPOT or BYBIT_FUTURES instead")
                    log.info(" !!! Connect: $it !!! ")
                }
                BINANCE -> ClientBinance(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                BITMAX -> ClientBitmax(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                BINANCE_FUTURES -> ClientBinanceFutures(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                GATE -> ClientGate(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                MEXC -> ClientMexc(api, sec).also { log.info(" !!! Connect: $it !!! ") }
                ONEINCH -> readConf("exchangeConfigs/ONEINCH.conf")!!.run {
                    ClientOneInch(api ?: getString("api"), sec ?: getString("sec"), this)
                        .also { log.info(" !!! Connect: $it !!! ") }
                }
                EXTENDED -> ClientExtended(api!!, sec).also { log.info(" !!! Connect: $it !!! ") }
                TEST -> ClientTestExchange()
                else -> throw UnsupportedClientException()
            }

        // TODO:: IMPLEMENT ONE CLIENT INSTANCE FOR EVERY EXCHANGE, DON'T CREATE NEW CLIENT FOR EVERY ACTION!!!
        fun ExchangeEnum.newClient(): Client = when (this) {
            BYBIT_SPOT -> readConf("exchangeConfigs/BYBIT_SPOT.conf")!!.run {
                ClientByBitSpot(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            BYBIT_FUTURES -> readConf("exchangeConfigs/BYBIT_FUTURES.conf")!!.run {
                ClientByBitFutures(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            BYBIT -> readConf("exchangeConfigs/BYBIT.conf")!!.run {
                ClientByBitSpot(getString("api"), getString("sec"))
                    .also {
                        log.warn("BYBIT is deprecated, use BYBIT_SPOT or BYBIT_FUTURES instead")
                        log.info(" !!! Connect: $it !!! ")
                    }
            }
            BINANCE -> readConf("exchangeConfigs/BINANCE.conf")!!.run {
                ClientBinance(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            BITMAX -> readConf("exchangeConfigs/BITMAX.conf")!!.run {
                ClientBitmax(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            BINANCE_FUTURES -> readConf("exchangeConfigs/BINANCE_FUTURES.conf")!!.run {
                ClientBinanceFutures(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            GATE -> readConf("exchangeConfigs/GATE.conf")!!.run {
                ClientGate(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            MEXC -> readConf("exchangeConfigs/MEXC.conf")!!.run {
                ClientMexc(getString("api"), getString("sec"))
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            ONEINCH -> readConf("exchangeConfigs/ONEINCH.conf")!!.run {
                ClientOneInch(getString("api"), getString("sec"), this)
                    .also { log.info(" !!! Connect: $it !!! ") }
            }
            EXTENDED -> readConf("exchangeConfigs/EXTENDED.conf")!!.run {
                ClientExtended(
                    getString("api"),
                    if (hasPath("starkKey") && getString("starkKey").isNotBlank()) getString("starkKey") else null
                ).also { log.info(" !!! Connect: $it !!! ") }
            }
            TEST -> ClientTestExchange()
            else -> throw UnsupportedClientException()
        }
    }
}