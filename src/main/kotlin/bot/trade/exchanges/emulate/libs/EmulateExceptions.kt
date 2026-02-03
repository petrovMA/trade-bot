package bot.trade.exchanges.emulate.libs

import java.lang.RuntimeException

class NoEmptyOrdersException(msg: String) : RuntimeException(msg)
class NotSupportedCandlestickIntervalException : RuntimeException()
class UnsupportedStateException(msg: String) : RuntimeException(msg)
class UnsupportedDataException(msg: String) : RuntimeException(msg)
class OrderNotFoundException(msg: String) : RuntimeException(msg)