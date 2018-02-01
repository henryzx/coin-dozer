import com.github.rjeschke.txtmark.Processor
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.QuoteMode
import org.jsoup.Jsoup
import java.io.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.regex.Pattern

fun main(args: Array<String>) {
    IMF().run()
}

/**
 * # TODO
 *
 * ## IMF.org
 *
 * 可以用RSS。地址: http://www.imf.org/external/rss/feeds.aspx?category=PUBS_wp
 *
 * 问题：数据不全
 *
 * ## fed
 *
 * 可以用RSS。地址: https://www.federalreserve.gov/feeds/ifdp.xml
 *
 *
 * 详情使用 Jsoap 解析并且入库
 *
 */
class IMF {

    fun run() {


        // 按页抓取方案
        val items = ArrayList<Item>()
        run {
            val pageLimit = 500 // for safe
            val pagePattern = Pattern.compile("""Page:.*(\d+).*of.*(\d+)""", Pattern.MULTILINE)
            val dateFormat = SimpleDateFormat("MMMM dd yyyy", Locale.US)

            for (page in 1..pageLimit) {

                val doc = Jsoup.connect("http://www.imf.org/en/Publications/Search?series=IMF%20Working%20Papers&when=During&year=2018&page=$page").get()
                val rows = doc.select("div.result-row.pub-row")

                val matcher = pagePattern.matcher(doc.selectFirst("p.pages").text())
                if (!matcher.matches()) throw IllegalStateException("can't find page")
                val curPage = matcher.group(1).toInt()
                val totalPage = matcher.group(2).toInt()

                if (curPage != page) break // page don't match
                if (page > totalPage) break // exceeded the end of page

                for (row in rows) {
                    // parse row

                    val item = Item(
                            title = row.select("a")[0].text(),
                            url = row.select("a")[0].absUrl("href"),
                            date = dateFormat.parse(row.select("p:last-child")[0].text().substring("Date: ".length).replace(",", ""))
                    )
                    println(item)
                    items.add(item)
                }

            }
        }

        val threadPool = Executors.newCachedThreadPool()
        val latch = CountDownLatch(items.size)
        for (item in items) {
            threadPool.submit {
                val doc = Jsoup.connect(item.url).get()
                val detail = Detail(
                        abstract = doc.selectFirst("p.pub-label:contains(Summary)").nextElementSibling().text(),
                        downloadUrl = doc.selectFirst("a:contains(Free Full Text)").absUrl("href")
                )
                item.detail = detail
                latch.countDown()
            }
        }

        latch.await()

        println("done with : " + items.joinToString { it.toString() })

        // export to csv
        run {
            val outputFile = File("""C:\Users\zheng\out.csv""")
            val printer = CSVPrinter(OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_16), CSVFormat.EXCEL.withHeader("date", "title", "pdf", "abstract"))
            for (item in items) {
                printer.printRecord(item.date, item.title, item.detail?.downloadUrl, item.detail?.abstract)
            }
            printer.close()
            println("wrote to file ${outputFile.path}")
        }


        // export to md
        run {
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

                ~by zhengxiao~
            """.trimIndent()

            val outputFile = File("""C:\Users\zheng\out.md""")
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

            FileWriter(File("""C:\Users\zheng\out.html""")).use {

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
        threadPool.shutdown()
    }

    data class Item(val title: String, val url: String, val date: Date, var detail: Detail? = null)
    data class Detail(val abstract: String, val downloadUrl: String)
}