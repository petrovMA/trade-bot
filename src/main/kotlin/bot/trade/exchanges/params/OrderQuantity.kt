package bot.trade.exchanges.params

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class OrderQuantity(
    @SerializedName("value") val value: BigDecimal,
    @SerializedName("is_counter_balance") val isCounterBalance: Boolean = false
)