package bot.trade.exchanges.clients


interface ClientFutures: Client {
    fun getPositions(pair: TradePair): List<Position>
    fun switchMode(category: String, mode: Int, pair: TradePair? = null, coin: String? = null)
}