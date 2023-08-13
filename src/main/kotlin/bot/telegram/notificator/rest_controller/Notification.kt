package bot.telegram.notificator.rest_controller

import java.math.BigDecimal

data class Notification(val amount: BigDecimal, val type: String, val botName: String, val price: BigDecimal? = null, val placeOrder: Boolean = true)
