import com.sun.syndication.io.SyndFeedInput
import com.sun.syndication.io.XmlReader
import java.net.URL

fun main(args: Array<String>) {
    val feedSource = URL("https://www.federalreserve.gov/feeds/working_papers.xml")
    val input = SyndFeedInput()
    val feed = input.build(XmlReader(feedSource))
    feed.entries.forEach { println(it) }
}