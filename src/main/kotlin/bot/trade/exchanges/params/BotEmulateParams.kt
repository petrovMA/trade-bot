package bot.trade.exchanges.params

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class BotEmulateParams(
    @SerializedName("from") val from: String?,
    @SerializedName("to") val to: String?,
    @SerializedName("fee") val fee: BigDecimal?,
    @SerializedName("fail_if_kline_gaps") val failIfKlineGaps: Boolean?,
    @SerializedName("bot_params") val botParams: BotSettings,
    @SerializedName("is_write_orders_to_log") val isWriteOrdersToLog: Boolean?
)
