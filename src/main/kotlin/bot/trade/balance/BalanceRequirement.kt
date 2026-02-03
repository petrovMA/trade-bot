package bot.trade.balance

import java.math.BigDecimal

data class BalanceRequirement(
    val firstToken: String,
    val secondToken: String,
    val requiredFirst: BigDecimal,
    val requiredSecond: BigDecimal,
    val totalOrders: Int,
    val buyOrders: Int,
    val sellOrders: Int,
    val currentPrice: BigDecimal,
    val orders: List<GridOrderDto>? = null  // List of planned orders
)