package exchange_api.oneinch.token

import com.typesafe.config.Config
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Bridges TradePair symbols ("BTC", "USDT") to BSC contract addresses.
 * Loads token addresses, decimals, and symbol mappings from ONEINCH.conf.
 */
class BscTokenRegistry(config: Config) {

    private val log = KotlinLogging.logger {}

    data class TokenInfo(
        val symbol: String,
        val address: String,
        val decimals: Int
    )

    private val symbolMapping: Map<String, String>
    private val tokensBySymbol: Map<String, TokenInfo>
    private val tokensByAddress: Map<String, TokenInfo>

    init {
        val mapping = mutableMapOf<String, String>()
        if (config.hasPath("symbol_mapping")) {
            val mappingConf = config.getConfig("symbol_mapping")
            for (entry in mappingConf.entrySet()) {
                mapping[entry.key.uppercase()] = entry.value.unwrapped().toString().uppercase()
            }
        }
        symbolMapping = mapping

        val tokens = mutableMapOf<String, TokenInfo>()
        val tokensConf = config.getConfig("tokens")
        for (entry in tokensConf.root().keys) {
            val tokenConf = tokensConf.getConfig(entry)
            val symbol = entry.uppercase()
            val info = TokenInfo(
                symbol = symbol,
                address = tokenConf.getString("address").lowercase(),
                decimals = tokenConf.getInt("decimals")
            )
            tokens[symbol] = info
        }
        tokensBySymbol = tokens
        tokensByAddress = tokens.values.associateBy { it.address }

        log.info("BscTokenRegistry initialized with ${tokens.size} tokens, ${mapping.size} symbol mappings")
    }

    /**
     * Resolves a trading symbol to the actual BSC token symbol.
     * e.g., "BTC" -> "BTCB" via symbol_mapping.
     */
    fun resolveSymbol(symbol: String): String {
        val upper = symbol.uppercase()
        return symbolMapping[upper] ?: upper
    }

    fun getAddress(symbol: String): String {
        val resolved = resolveSymbol(symbol)
        return tokensBySymbol[resolved]?.address
            ?: throw IllegalArgumentException("Unknown token symbol: $symbol (resolved: $resolved)")
    }

    fun getDecimals(symbol: String): Int {
        val resolved = resolveSymbol(symbol)
        return tokensBySymbol[resolved]?.decimals
            ?: throw IllegalArgumentException("Unknown token symbol: $symbol (resolved: $resolved)")
    }

    /**
     * Convert a human-readable amount to wei (smallest unit).
     * e.g., toWei(1.5, "USDT") with 18 decimals = 1500000000000000000
     */
    fun toWei(amount: BigDecimal, symbol: String): BigInteger {
        val decimals = getDecimals(symbol)
        return amount.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()
    }

    /**
     * Convert a wei amount back to human-readable.
     * e.g., fromWei(1500000000000000000, "USDT") with 18 decimals = 1.5
     */
    fun fromWei(weiAmount: BigInteger, symbol: String): BigDecimal {
        val decimals = getDecimals(symbol)
        return BigDecimal(weiAmount).divide(BigDecimal.TEN.pow(decimals))
    }

    fun fromWei(weiAmount: String, symbol: String): BigDecimal {
        return fromWei(BigInteger(weiAmount), symbol)
    }

    fun getSymbol(address: String): String? {
        return tokensByAddress[address.lowercase()]?.symbol
    }

    fun getTokenInfo(symbol: String): TokenInfo? {
        val resolved = resolveSymbol(symbol)
        return tokensBySymbol[resolved]
    }

    fun hasToken(symbol: String): Boolean {
        val resolved = resolveSymbol(symbol)
        return tokensBySymbol.containsKey(resolved)
    }
}
