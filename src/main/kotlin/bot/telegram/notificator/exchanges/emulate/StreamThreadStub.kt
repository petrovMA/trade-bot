package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.clients.stream.Stream

class StreamThreadStub : Stream() {
    override fun run() {}
}