package bot.trade.exchanges.libs.bitmax

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.builder.ToStringBuilder

@JsonIgnoreProperties(ignoreUnknown = true)
class Product {
    @JsonProperty("symbol")
    var symbol: String? = null

    @JsonProperty("domain")
    var domain: String? = null

    @JsonProperty("baseAsset")
    var baseAsset: String? = null

    @JsonProperty("quoteAsset")
    var quoteAsset: String? = null

    @JsonProperty("priceScale")
    var priceScale = 0

    @JsonProperty("qtyScale")
    var qtyScale = 0

    @JsonProperty("notionalScale")
    var notionalScale = 0

    @JsonProperty("minQty")
    var minQty = 0.0

    @JsonProperty("maxQty")
    var maxQty = 0.0

    @JsonProperty("minNotional")
    var minNotional = 0.0

    @JsonProperty("maxNotional")
    var maxNotional = 0.0

    @JsonProperty("status")
    var status: String? = null

    @JsonProperty("miningStatus")
    var miningStatus: String? = null

    @JsonProperty("marginTradable")
    var marginTradable: String? = null
    override fun toString(): String {
        return ToStringBuilder(this)
            .append("symbol", symbol)
            .append("domain", domain)
            .append("baseAsset", baseAsset)
            .append("quoteAsset", quoteAsset)
            .append("priceScale", priceScale)
            .append("qtyScale", qtyScale)
            .append("notionalScale", notionalScale)
            .append("minQty", minQty)
            .append("maxQty", maxQty)
            .append("minNotional", minNotional)
            .append("maxNotional", maxNotional)
            .append("status", status)
            .append("miningStatus", miningStatus)
            .toString()
    }
}