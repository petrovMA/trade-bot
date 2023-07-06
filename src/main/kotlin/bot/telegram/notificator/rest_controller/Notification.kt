package bot.telegram.notificator.rest_controller

import java.math.BigDecimal

data class Notification(val amount: Double, val type: String, val pair: String)
data class RatioSetting(val buyRatio: BigDecimal = BigDecimal(1), val sellRatio: BigDecimal = BigDecimal(1))
