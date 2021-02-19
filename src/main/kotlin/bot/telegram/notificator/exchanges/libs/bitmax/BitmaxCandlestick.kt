package bot.telegram.notificator.exchanges.libs.bitmax

import com.google.gson.annotations.SerializedName
import org.apache.commons.lang3.builder.ToStringBuilder

class BitmaxCandlestick {
    @SerializedName("m")
    var message: String? = null

    @SerializedName("s")
    var symbol: String? = null

    @SerializedName("ba")
    var baseAsset: String? = null

    @SerializedName("qa")
    var quoteAsset: String? = null

    @SerializedName("i")
    var interval: String? = null

    @SerializedName("t")
    var time: Long = 0

    @SerializedName("o")
    var open: String? = null

    @SerializedName("c")
    var close: String? = null

    @SerializedName("h")
    var high: String? = null

    @SerializedName("l")
    var low: String? = null

    @SerializedName("v")
    var volume: String? = null
    override fun toString(): String {
        return ToStringBuilder(this)
            .append("message", message)
            .append("symbol", symbol)
            .append("baseAsset", baseAsset)
            .append("quoteAsset", quoteAsset)
            .append("interval", interval)
            .append("time", time)
            .append("open", open)
            .append("close", close)
            .append("high", high)
            .append("low", low)
            .append("volume", volume)
            .toString()
    }
}