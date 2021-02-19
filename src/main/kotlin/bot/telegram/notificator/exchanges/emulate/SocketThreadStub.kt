package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.clients.socket.SocketThread

class SocketThreadStub : SocketThread() {
    override fun run() {}
}