package bot.telegram.notificator

data class Commands(
        val commandStatus: Regex = "/?status".toRegex(),
        val commandScan: Regex = "/?scan".toRegex(),
        val commandCreateAll: Regex = "/?create all".toRegex(),
        val commandCreate: Regex = "/?create\\s+[\\w-]{3,15}".toRegex(),
        val commandStart: Regex = "/?start\\s+[\\w-]{3,15}".toRegex(),
        val commandStartAll: Regex = "/?start all".toRegex(),
        val commandBalance: Regex = "/?[Bb]alance(\\s([\\w-]{3,15}))*".toRegex(),
        val commandAllBalance: Regex = "/?[Aa]ll[Bb]alance(\\s([\\w-]{3,15}))*".toRegex(),
        val commandOrders: Regex = "/?orders(\\s([\\w-]{3,15})){2}".toRegex(),
        val commandAllOrders: Regex = "/?orders\\s+[Aa]ll".toRegex(),
        val commandStopAll: Regex = "/?stop\\s+[Aa]ll".toRegex(),
        val commandStop: Regex = "/?stop\\s+[\\w-]{3,15}".toRegex(),
        val commandDelete: Regex = "/?delete\\s+[\\w-]{3,15}".toRegex(),
        val commandShowProp: Regex = "/?showProperties\\s+[\\w-]{3,15}".toRegex(),
        val commandQueueSize: Regex = "/?queueSize\\s+[\\w-]{3,15}".toRegex(),
        val commandDeleteOldCandlestickData: Regex = "/?[Dd]elete[Oo]ld[Cc]andlestick[Dd]ata\\s+[A-z]{4,11}\\s+\\d{4}_\\d{2}_\\d{2}".toRegex(),
        val commandReset: Regex = "/?reset\\s+[\\w-]{3,15}".toRegex(),
        val commandCandlestickData: Regex = "/?candlestick\\s+(WRITE_AND_CHECK|WRITE|CHECK|write_and_check|write|check)".toRegex(),
        val commandCalcGap: Regex = "/?gap\\s+[\\w-]{3,15}".toRegex(),
        val commandEmulate: Regex = "/?[Ee]mulate\\s+[A-z]{4,11}\\s+.{1,50}\\s+\\d{4}_\\d{2}_\\d{2}\\s+\\d{4}_\\d{2}_\\d{2}".toRegex(),
        val commandTradePairsInit: Regex = "/?[Tt]radePairs\\s+[Ii]nit".toRegex()
) {
    override fun toString(): String = """
        commandStatus = $commandStatus
        commandScan = $commandScan
        commandCreateAll = $commandCreateAll
        commandCreate = $commandCreate
        commandStart = $commandStart
        commandStartAll = $commandStartAll
        commandFreeBalance = $commandBalance
        commandAllBalance = $commandAllBalance
        commandOrders = $commandOrders
        commandAllOrders = $commandAllOrders
        commandStopAll = $commandStopAll
        commandStop = $commandStop
        commandDelete = $commandDelete
        commandShowProp = $commandShowProp
        commandQueueSize = $commandQueueSize
        commandReset = $commandReset
        commandCandlestickData = $commandCandlestickData
        commandCalcGap = $commandCalcGap
        commandEmulate = $commandEmulate
        commandDeleteOldCandlestickData = $commandDeleteOldCandlestickData
        commandTradePairsInit = $commandTradePairsInit
    """.trimIndent()
}