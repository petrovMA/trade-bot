package bot.trade.database.data.entities

import bot.trade.exchanges.clients.SIDE
import jakarta.persistence.*
import java.math.BigDecimal
import java.sql.Timestamp

@Entity
@Table(name = "ORDERS")
data class Order(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID") val id: Long? = null,

    @Column(name = "BOT_NAME") val botName: String? = null,

    @Column(name = "ORDER_ID", unique = true) val orderId: String? = null,

    @Column(name = "TRADE_PAIR") val tradePair: String? = null,

    @Column(name = "AMOUNT", precision = 20, scale = 8) val amount: BigDecimal? = null,

    @Column(name = "SIDE") val orderSide: SIDE? = null,

    @Column(name = "PRICE", precision = 20, scale = 8) val price: BigDecimal? = null,

    @Column(name = "DATE_TIME") val dateTime: Timestamp? = null,

    @Enumerated(EnumType.STRING) val notificationType: NotificationType? = null,
) {
    override fun equals(other: Any?): Boolean = other is Order &&
            other.id == this.id &&
            other.botName == this.botName &&
            other.amount == this.amount &&
            other.price == this.price &&
            other.orderId == this.orderId &&
            other.dateTime == this.dateTime &&
            other.tradePair == this.tradePair &&
            other.orderSide == this.orderSide &&
            other.notificationType == this.notificationType

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , botName = $botName , amount = $amount , price = $price , dateTime = $dateTime , notificationType = $notificationType )"
    }
}
