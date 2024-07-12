package bot.trade.exchanges.params

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class Param(
    @SerializedName("value") val value: BigDecimal,
    @SerializedName("use_percent") val usePercent: Boolean = false
)