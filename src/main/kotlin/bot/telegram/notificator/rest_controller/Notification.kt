package bot.telegram.notificator.rest_controller

import java.math.BigDecimal

data class Notification(val amount: BigDecimal, val type: String, val pair: String, val price: BigDecimal? = null, val placeOrder: Boolean = true)
data class RatioSetting(val buyRatio: BigDecimal = BigDecimal(1), val sellRatio: BigDecimal = BigDecimal(1))
