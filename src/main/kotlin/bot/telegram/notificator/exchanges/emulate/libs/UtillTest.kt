package bot.telegram.notificator.exchanges.emulate.libs

import bot.telegram.notificator.exchanges.emulate.Emulate.EmulateResult
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream


fun writeIntoExcel(file: File, lines: Iterable<EmulateResult>, head: List<String>): File = HSSFWorkbook().let {

    it.createSheet("sheet").let { sheet ->

        val writeRov = { result: EmulateResult, s: HSSFSheet, rowNum: Int ->
            val row = s.createRow(rowNum)
            var cell = -1

            row.createCell(++cell).setCellValue(result.pair)
            row.createCell(++cell).setCellValue(result.profitByLastPrice)
            row.createCell(++cell).setCellValue(result.profitByFirstPrice)
            row.createCell(++cell).setCellValue(result.executeOrderCount)
            row.createCell(++cell).setCellValue(result.updateStaticOrderCount)
            row.createCell(++cell).setCellValue(result.firstBalance)
            row.createCell(++cell).setCellValue(result.secondBalance)
            row.createCell(++cell).setCellValue(result.secondBalanceByFirstPrice)
            row.createCell(++cell).setCellValue(result.secondBalanceByLastPrice)
            row.createCell(++cell).setCellValue(result.from)
            row.createCell(++cell).setCellValue(result.to)
            ++cell
            result.buyProfitPercent?.run { row.createCell(++cell).setCellValue(this) }
            result.sellProfitPercent?.run { row.createCell(++cell).setCellValue(this) }
            result.candlesBuyInterval?.run { row.createCell(++cell).setCellValue(this) }
            result.candlesSellInterval?.run { row.createCell(++cell).setCellValue(this) }
            result.updStaticOrders?.run { row.createCell(++cell).setCellValue(this) }
        }

        val row = sheet.createRow(0)
        for (j in head.indices) {
            row.createCell(j).setCellValue(head[j])
        }

        for (i in lines.iterator())
            writeRov(i, sheet, sheet.lastRowNum + 1)

        it.write(FileOutputStream(file))
        it.close()

        file
    }
}