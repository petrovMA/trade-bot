package exchange_api.bybit.websocket.messages.response

data class Insurance(
    val `data`: List<Data>,
    val topic: String
) {
    data class Data(
        val currency: String,
        val timestamp: String,
        val wallet_balance: Long
    )
}