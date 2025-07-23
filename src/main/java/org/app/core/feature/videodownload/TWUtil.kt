package org.app.core.feature.videodownload

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object TWUtil {
    fun fetch(response: String?): DownloadInfo? {
        try {
            val doc = Jsoup.parse(response ?: return null)
            val links = mutableListOf<LinkInfo>()
            val thumbnail = getThumbnail(doc)

            val elements = doc.getElementsByTag("tbody")[0].getElementsByTag("tr")
            for (i in elements.indices) {
                val items = elements[i]
                val url = getUrl(items)
                val size = getSize(items)
                if (url != null && size != null) {
                    val link = LinkInfo(
                        url,
                        size,
                        "mp4",
                        "",
                        0,
                        size
                    )
                    links.add(link)
                }
            }

            if (links.isNotEmpty()) {
                return DownloadInfo(links, thumbnail, SupportLink.TT.getPrefixName() + ".mp4")
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    private fun getSize(input: Element): String? {
        val elements = input.getElementsByTag("td")
        val txt = StringBuilder()
        for (i in elements.indices) {
            var s = elements[i].html()
            if (!elements[i].text().isEmpty() && !elements[i].text().contains("ownload")) {
                txt.append(elements[i].text()).append(" ")
            }
            if (!s.startsWith("<") && s.contains("x")) {
                if (s.contains(" ")) {
                    s = s.replace(" ", "")
                }
                return s
            }
        }

        return if (txt.toString().lowercase().contains("mp3")) null else txt.toString()
    }

    private fun getUrl(data: Element): String? {
        val regex = "preview_video\\('(.*)'"
        val pattern = Pattern.compile(regex, Pattern.MULTILINE)
        val matcher = pattern.matcher(data.html())
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    private fun getThumbnail(doc: Document) : String {
        val elements = doc.getElementsByClass("img-thumbnail")
        return elements.firstOrNull()?.attr("src") ?: ""
    }
}