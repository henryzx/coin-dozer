import com.github.rjeschke.txtmark.Processor
import models.Item
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

interface Exporter {
    fun export(items: ArrayList<Item>)
}

class MarkdownExporter(
        private val outputFilePath: String = """C:\Users\zheng\out.html""",
        private val outputFilePathMarkdown: String = """C:\Users\zheng\out.md"""
) : Exporter {


    override fun export(items: ArrayList<Item>) {
        val templateHeader = """
                    IMF Summary on 2018
                    =================================

                    updated on: <update_date>

                """.trimIndent()


        val templateContent = """
                    [<title>](<url>)
                    -----------------------

                    **Publish Date:** <date>

                    ### Abstract

                    <abstract>

                    [Download PDF](<download_url>)


                """.trimIndent()

        val templateFooter = """

                    ----------------------

                    ~by xueying ❤ zhengxiao~
                """.trimIndent()

        val outputFile = File(outputFilePathMarkdown)
        val writer = FileWriter(outputFile)

        writer.use {

            val sdf = SimpleDateFormat("yyyy-MM-dd")
            sdf.timeZone = TimeZone.getTimeZone("GMT+8")

            it.apply {

                write(templateHeader.replace("<update_date>", sdf.format(Date())))
                write("\n")

                for (item in items) {
                    write(templateContent
                            .replace("<title>", item.title)
                            .replace("<url>", item.url)
                            .replace("<date>", sdf.format(item.date))
                            .replace("<abstract>", item.detail?.abstract ?: "")
                            .replace("<download_url>", item.detail?.downloadUrl ?: "")
                    )
                }

                write(templateFooter)
            }
        }

        FileWriter(File(outputFilePath)).use {

            val htmlHeader = """
                        <html><head><meta charset="utf-8">
                        <style type="text/css">
                    """.trimIndent()

            val htmlHeaderAfterCss = """
                        </style></head>
                        <body>
                    """.trimIndent()

            val htmlFooter = """
                        </body></html>
                    """.trimIndent()
            it.apply {
                write(htmlHeader)
                InputStreamReader(javaClass.getResourceAsStream("/markdown.css")).use {
                    it.copyTo(this)
                }
                write(htmlHeaderAfterCss)
                write(Processor.process(outputFile))
                write(htmlFooter)
            }
        }
    }

}


class CSVExporter(private val outputFilePath: String = """C:\Users\zheng\out.csv""") : Exporter {

    override fun export(items: ArrayList<Item>) {
        val outputFile = File(outputFilePath)
        val printer = CSVPrinter(OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_16), CSVFormat.EXCEL.withHeader("date", "title", "pdf", "abstract"))
        for (item in items) {
            printer.printRecord(item.date, item.title, item.detail?.downloadUrl, item.detail?.abstract)
        }
        printer.close()
        println("wrote to file ${outputFile.path}")
    }

}