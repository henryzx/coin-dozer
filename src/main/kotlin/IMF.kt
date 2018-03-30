import models.Detail
import models.Item
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.regex.Pattern

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
fun main(args: Array<String>) {
    imf()
}

fun imf() {

    val exporter = MarkdownExporter(title = "IMF Summary on 2018")

    println("fetch start")
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
                        date = run {
                            val date = row.selectFirst("p:contains(Date:)").text().substring("Date: ".length).replace(",", "")
                            return@run try {
                                dateFormat.parse(date)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Date()
                            }
                        }
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

    println("fetch done with : " + items.joinToString { it.toString() })

    // export
    exporter.export(items)

    threadPool.shutdown()
}
