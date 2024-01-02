package bot.trade.exchanges.clients


interface ClientFutures: Client {
    fun getPositions(pair: TradePair): List<Position>
}