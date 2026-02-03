package bot.trade.exchanges

import bot.trade.balance.BalanceRequirement
import bot.trade.balance.GridOrderDto
import bot.trade.database.data.entities.ActiveOrder
import bot.trade.exchanges.clients.DIRECTION
import bot.trade.exchanges.clients.SIDE
import bot.trade.exchanges.clients.TYPE
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.exchanges.params.OrderQuantity
import bot.trade.exchanges.params.Param
import bot.trade.libs.UnknownOrderSide
import bot.trade.libs.div8
import bot.trade.libs.percent
import bot.trade.libs.round
import java.math.BigDecimal
import kotlin.collections.sumOf


class GridOrders {
    val orders = mutableListOf<ActiveOrder>()
    val botSettings: BotSettingsGrid
    val startPrice: BigDecimal

    companion object {
        fun calcAmount(
            orderQuantity: OrderQuantity,
            price: BigDecimal,
            countOfDigitsAfterDotForAmount: Int
        ): BigDecimal =
            if (orderQuantity.isCounterBalance) orderQuantity.value.div8(price).round(countOfDigitsAfterDotForAmount)
            else orderQuantity.value

        fun orderDistance(currPrice: BigDecimal, orderDistance: Param): BigDecimal =
            if (orderDistance.usePercent) currPrice.percent(orderDistance.value)
            else orderDistance.value
    }

    constructor(currentPrice: BigDecimal, botSettings: BotSettingsGrid) {
        var price = botSettings.parameters.tradingRange.lowerBound
        this.botSettings = botSettings
        this.startPrice = currentPrice

        while (price < botSettings.parameters.tradingRange.upperBound) {
            val side = if (price <= currentPrice) SIDE.BUY else SIDE.SELL
            orders.add(
                ActiveOrder(
                    tradePair = botSettings.pair.let { "${it.first}${it.second}" },
                    amount = calcAmount(
                        botSettings.parameters.orderQuantity,
                        price,
                        botSettings.countOfDigitsAfterDotForAmount
                    ),
                    orderSide = side,
                    price = price,
                    stopPrice = run {
                        val profitDistance = orderDistance(price, botSettings.parameters.profitDistance)
                        val stopPrice = when (botSettings.direction) {
                            DIRECTION.LONG -> price + profitDistance
                            DIRECTION.SHORT -> price - profitDistance
                        }
                        // Apply specific scale only for percentage-based calculations
                        if (botSettings.parameters.profitDistance.usePercent) {
                            stopPrice.setScale(
                                botSettings.countOfDigitsAfterDotForPrice,
                                java.math.RoundingMode.HALF_UP
                            )
                        } else stopPrice
                    },
                    lastBorderPrice = currentPrice,
                    direction = botSettings.direction,
                    botName = botSettings.name
                )
            )
            price += orderDistance(price, botSettings.parameters.orderDistance)
        }
    }

    fun calculateRequiredBalance(): BalanceRequirement {
        val (sellOrders, buyOrders) = orders
            .run { filter { it.orderSide == SIDE.SELL } to filter { it.orderSide == SIDE.BUY } }

        // Calculate raw requirements
        val rawRequiredFirst = sellOrders.sumOf { it.amount ?: BigDecimal(0) }
        val rawRequiredSecond = buyOrders.sumOf { it.amount?.times(it.price ?: BigDecimal(0)) ?: BigDecimal(0) }

        // Apply leverage (if null or <= 0, default to 1x)
        val leverage = botSettings.leverage?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

        // Convert orders to DTO format for API response
        val orderDtos = orders.map { order ->
            GridOrderDto(
                price = order.price ?: BigDecimal.ZERO,
                amount = order.amount ?: BigDecimal.ZERO,
                side = when (order.orderSide) {
                    SIDE.BUY -> "BUY"
                    SIDE.SELL -> "SELL"
                    else -> "UNKNOWN"
                }
            )
        }

        return BalanceRequirement(
            firstToken = botSettings.pair.first,
            secondToken = botSettings.pair.second,
            requiredFirst = rawRequiredFirst.divide(leverage, 8, java.math.RoundingMode.HALF_UP),
            requiredSecond = rawRequiredSecond.divide(leverage, 8, java.math.RoundingMode.HALF_UP),
            totalOrders = orders.size,
            buyOrders = buyOrders.size,
            sellOrders = sellOrders.size,
            currentPrice = startPrice,
            orders = orderDtos  // Include list of planned orders
        )
    }
}