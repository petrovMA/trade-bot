package bot.trade.database.data.entities

import bot.trade.exchanges.clients.SIDE
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "ACTIVE_ORDERS")
data class ActiveOrder(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID") val id: Long? = null,

    @Column(name = "BOT_NAME") val botName: String? = null,

    @Column(name = "ORDER_ID", unique = true) val orderId: String? = null,

    @Column(name = "TRADE_PAIR") val tradePair: String? = null,

    @Column(name = "AMOUNT", precision = 20, scale = 8) val amount: BigDecimal? = null,

    @Column(name = "SIDE") val orderSide: SIDE? = null,

    @Column(name = "PRICE", precision = 20, scale = 8) val price: BigDecimal? = null,

    @Column(name = "STOP_PRICE", precision = 20, scale = 8) val stopPrice: BigDecimal? = null,

    @Column(name = "LAST_BORDER_PRICE", precision = 20, scale = 8) val lastBorderPrice: BigDecimal? = null
) {
    override fun equals(other: Any?): Boolean = other is ActiveOrder &&
            other.id == this.id &&
            other.botName == this.botName &&
            other.amount == this.amount &&
            other.price == this.price &&
            other.orderId == this.orderId &&
            other.tradePair == this.tradePair &&
            other.orderSide == this.orderSide &&
            other.stopPrice == this.stopPrice &&
            other.lastBorderPrice == this.lastBorderPrice

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString() = this::class.simpleName + "(id = $id, botName = $botName, amount = $amount, " +
            "price = $price, orderId = $orderId, tradePair = $tradePair, orderSide = $orderSide, " +
            "stopPrice = $stopPrice, lastBorderPrice = $lastBorderPrice)"
}
