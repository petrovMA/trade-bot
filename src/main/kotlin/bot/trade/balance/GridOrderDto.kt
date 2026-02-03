package bot.trade.balance

import java.math.BigDecimal

/**
 * DTO for grid order information in balance calculation response
 */
data class GridOrderDto(
    val price: BigDecimal,
    val amount: BigDecimal,
    val side: String  // "BUY" or "SELL"
)
