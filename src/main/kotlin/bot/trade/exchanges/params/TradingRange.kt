package bot.trade.exchanges.params

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class TradingRange(
    @SerializedName("lower_bound") val lowerBound: BigDecimal,
    @SerializedName("upper_bound") val upperBound: BigDecimal
)