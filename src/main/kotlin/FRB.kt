import com.sun.syndication.feed.synd.SyndEntry
import com.sun.syndication.io.SyndFeedInput
import com.sun.syndication.io.XmlReader
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import models.Detail
import models.Item
import org.jsoup.Jsoup
import java.net.URI
import java.net.URL

fun main(args: Array<String>) {
    frb()
}

fun frb() {
    val feedSource = URL("https://www.federalreserve.gov/feeds/working_papers.xml")
    val input = SyndFeedInput()
    val feed = input.build(XmlReader(feedSource))

    val items = Observable.fromIterable(feed.entries).map {
        val entry = it as SyndEntry
        val title = entry.title
        val url = entry.uri
        val date = entry.publishedDate
        val detail = Detail(
                abstract = entry.description.value,
                downloadUrl = ""
        )
        Item(title, url, date, detail)
    }.observeOn(Schedulers.io()).flatMap { item ->
        // get downloadUrls
        Observable.fromCallable {
            val uri = URI(item.url)
            val anchorID = uri.fragment
            Jsoup.connect(item.url).get().selectFirst("#$anchorID h5 a")?.absUrl("href")?.let {
                item.detail = item.detail?.copy(downloadUrl = it)
            }
            item
        }
    }.subscribeOn(Schedulers.trampoline()).toList().blockingGet()

    MarkdownExporter(title = "FRB: Finance and Economics Discussion Series Working Papers").export(items)

}