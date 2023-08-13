package bot.telegram.notificator.database.data.entities

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

    @Column(name = "AMOUNT") val amount: BigDecimal? = null,

    @Column(name = "PRICE") val price: BigDecimal? = null,

    @Column(name = "DATE_TIME") val dateTime: Timestamp? = null,

    @Enumerated(EnumType.STRING) val notificationType: NotificationType? = null,
) {
    override fun equals(other: Any?): Boolean = other is Order &&
            other.id == this.id &&
            other.botName == this.botName &&
            other.amount == this.amount &&
            other.price == this.price &&
            other.dateTime == this.dateTime &&
            other.notificationType == this.notificationType

    override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , botName = $botName , amount = $amount , price = $price , dateTime = $dateTime , notificationType = $notificationType )"
    }
}
