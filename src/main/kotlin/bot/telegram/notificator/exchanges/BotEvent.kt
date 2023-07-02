package bot.telegram.notificator.exchanges

import bot.telegram.notificator.exchanges.clients.CommonExchangeData

class BotEvent(val text: String = "", val type: Type) : CommonExchangeData {
    enum class Type {
        GET_PAIR_OPEN_ORDERS,
        GET_ALL_OPEN_ORDERS,
        SHOW_ALL_BALANCES,
        SHOW_FREE_BALANCES,
        SHOW_BALANCES,
        SHOW_GAP,
        INTERRUPT,
        CREATE_ORDER
    }
}