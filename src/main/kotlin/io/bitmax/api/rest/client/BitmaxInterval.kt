package io.bitmax.api.rest.client

import bot.telegram.notificator.exchanges.clients.INTERVAL

/**
 * Interval enumeration interval's for bars history request
 */
enum class BitmaxInterval(val interval: String) {
    ONE_MINUTE("1"),
    FIVE_MINUTES("5"),
    FIFTEEN_MINUTES("15"),
    HALF_HOURLY("30"),
    HOURLY("60"),
    TWO_HOURLY("120"),
    FOUR_HOURLY("240"),
    SIX_HOURLY("360"),
    TWELVE_HOURLY("720"),
    DAILY("1d"),
    WEEKLY("1w"),
    MONTHLY("1m");

    companion object {
        fun from(findValue: String) = values().first { it.interval == findValue }
    }

    fun toInterval(): INTERVAL = when (this) {
        ONE_MINUTE -> INTERVAL.ONE_MINUTE
        FIVE_MINUTES -> INTERVAL.FIVE_MINUTES
        FIFTEEN_MINUTES -> INTERVAL.FIFTEEN_MINUTES
        HALF_HOURLY -> INTERVAL.HALF_HOURLY
        HOURLY -> INTERVAL.HOURLY
        TWO_HOURLY -> INTERVAL.TWO_HOURLY
        FOUR_HOURLY -> INTERVAL.FOUR_HOURLY
        SIX_HOURLY -> INTERVAL.SIX_HOURLY
        TWELVE_HOURLY -> INTERVAL.TWELVE_HOURLY
        DAILY -> INTERVAL.DAILY
        WEEKLY -> INTERVAL.WEEKLY
        MONTHLY -> INTERVAL.MONTHLY
    }
}