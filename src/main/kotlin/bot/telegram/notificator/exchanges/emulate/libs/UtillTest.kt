package bot.telegram.notificator.exchanges.emulate.libs

import bot.telegram.notificator.exchanges.emulate.Emulate
import bot.telegram.notificator.exchanges.emulate.EmulateNew
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream


fun writeIntoExcel(file: File, lines: Iterable<Emulate.EmulateResult>, head: List<String>): File = HSSFWorkbook().let {

    it.createSheet("sheet").let { sheet ->

        val writeRov = { result: Emulate.EmulateResult, s: HSSFSheet, rowNum: Int ->
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

fun writeIntoExcelNew(file: File, lines: Iterable<EmulateNew.EmulateResult>, head: List<String>): File = HSSFWorkbook().let {

    it.createSheet("sheet").let { sheet ->

        sheet.setColumnWidth(0, 3000)
        sheet.setColumnWidth(1, 4000)
        sheet.setColumnWidth(2, 4000)
        sheet.setColumnWidth(3, 4000)
        sheet.setColumnWidth(4, 4000)
        sheet.setColumnWidth(5, 4000)
        sheet.setColumnWidth(6, 4000)
        sheet.setColumnWidth(7, 4000)
        sheet.setColumnWidth(8, 4000)
        sheet.setColumnWidth(9, 4000)
        sheet.setColumnWidth(10, 4000)
        sheet.setColumnWidth(11, 4000)

        val writeRov = { result: EmulateNew.EmulateResult, s: HSSFSheet, rowNum: Int ->
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
            row.apply {
                height = 1100
                createCell(j).setCellValue(head[j])
            }
        }

        for (i in lines.iterator())
            writeRov(i, sheet, sheet.lastRowNum + 1)

        it.write(FileOutputStream(file))
        it.close()

        file
    }
}