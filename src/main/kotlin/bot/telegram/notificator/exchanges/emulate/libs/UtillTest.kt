package bot.telegram.notificator.exchanges.emulate.libs

import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream


fun writeIntoExcel(file: File, lines: Iterable<List<Any>>): File = HSSFWorkbook().let {
    it.createSheet("sheet").let { sheet ->

        val writeRov = { array: List<Any>, s: HSSFSheet, rowNum: Int ->
            val row = s.createRow(rowNum)
            for (j in array.indices) {
                row.createCell(j).apply {
                    when (val value = array[j]) {
                        is Double -> setCellValue(value)
                        is String -> setCellValue(value)
                        is Int -> setCellValue(value.toString())
                        else -> throw UnsupportedDataException("Class: '${value::class.java}' not 'Double' or 'String' class")
                    }
                }
            }
        }

        for (i in lines.iterator())
            writeRov(i, sheet, sheet.lastRowNum + 1)

        it.write(FileOutputStream(file))
        it.close()

        file
    }
}