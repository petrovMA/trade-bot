package bot.telegram.notificator.libs

import java.time.Duration
import java.time.LocalDateTime


class ActionInterval(val interval: Duration, var lastCall: LocalDateTime = "2000_01_01".toLocalDate().atStartOfDay()) {

    /**
     * Do an action if past time from prev action more than interval
     * */
    fun <T> tryInvoke(action: () -> T): T? {
        val now = LocalDateTime.now()
        return if (now.isAfter(lastCall + interval)) {
            lastCall = now
            action()
        } else null
    }
}