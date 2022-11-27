package bot.telegram.notificator.libs

import java.lang.RuntimeException

class NotFoundApiAndSecCodes : RuntimeException()
class UnsupportedClientException : RuntimeException()
class UnsupportedOrderTypeException(msg: String) : RuntimeException(msg)
class UnsupportedOrderSideException : RuntimeException()
class UnsupportedEnumOrderIntervalException : RuntimeException()
class ConfigNotFoundException(msg: String) : RuntimeException(msg)
class NotSupportedIntervalException : RuntimeException()
class UnsupportedExchangeException : RuntimeException()
class UnsupportedBitMaxIntervalException : RuntimeException()
class BalanceNotFoundException : RuntimeException()
class UnknownIntervalException : RuntimeException()
class UnauthorizedException(msg: String) : RuntimeException(msg)
class UnsupportedOrderBookException(msg: String) : RuntimeException(msg)
class UnknownOrderSide(msg: String) : RuntimeException(msg)
class UnknownOrderStatus(msg: String) : RuntimeException(msg)
class NotEnoughBalanceException(msg: String) : RuntimeException(msg)