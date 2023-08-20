package bot.trade

data class Commands(
        val commandHelp: Regex = "/?help\\s+[A-z,\\d]+".toRegex(),
        val commandCreateTradeBot: Regex = "/?create[\\s,|]+[|A-z,.\\d\\s:-]+".toRegex(),
        val commandStartTradeBot: Regex = "/?start\\s[a-zA-Z0-9_]+$".toRegex(),
        val commandEmulateTradeBot: Regex = "/?emulate[\\s,|]+[|A-z,.\\d\\s:-]+".toRegex(),
        val commandLoadTradeBot: Regex = "/?load\\s[a-zA-Z0-9_]+$".toRegex(),


        val commandStatus: Regex = "/?status".toRegex(),
        val commandScan: Regex = "/?scan".toRegex(),
        val commandCreateAll: Regex = "/?create all".toRegex(),
        val commandCreate: Regex = "/?[Cc]reate\\s+[\\w:,\"{}\\.\\s]+".toRegex(),
        val commandUpdate: Regex = "/?[Uu]pdate\\s+[\\w:,\"{}\\.\\s]+".toRegex(),
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
        val commandFindParams: Regex = "/?[Ff]indParams\\s+[A-z]{4,11}\\s+.{1,50}\\s+\\d{4}_\\d{2}_\\d{2}\\s+\\d{4}_\\d{2}_\\d{2}".toRegex(),
        val commandTradePairsInit: Regex = "/?[Tt]radePairs\\s+[Ii]nit".toRegex(),
        val commandCollect: Regex = "/?[Cc]ollect\\s+[A-z]{4,11}\\s+[A-z]{3,100}".toRegex(),
        val commandSettings: Regex = "/?[Ss]ettings\\s+[\\w:,\"{}\\.\\s]+".toRegex()
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