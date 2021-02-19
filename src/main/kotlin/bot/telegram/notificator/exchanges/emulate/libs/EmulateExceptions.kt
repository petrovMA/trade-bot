package bot.telegram.notificator.exchanges.emulate.libs

import java.lang.RuntimeException

class NoEmptyOrdersException(msg: String) : RuntimeException(msg)
class NotSupportedCandlestickIntervalException : RuntimeException()
class UnsupportedStateException : RuntimeException()
class UnsupportedDataException(msg: String) : RuntimeException(msg)