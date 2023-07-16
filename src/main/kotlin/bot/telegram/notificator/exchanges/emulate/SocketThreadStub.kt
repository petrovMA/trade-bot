package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.clients.socket.Stream

class SocketThreadStub : Stream() {
    override fun run() {}
}