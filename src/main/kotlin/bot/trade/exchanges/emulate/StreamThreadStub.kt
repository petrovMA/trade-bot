package bot.trade.exchanges.emulate

import bot.trade.exchanges.clients.stream.Stream

class StreamThreadStub : Stream() {
    override fun run() {}
}