package org.app.core.feature.videodownload

import java.util.regex.Pattern

object FBUtil {
    fun getFbLink(source: String?, hd: Boolean): String? {
        if (source != null) {
            val end = "download="
            val start = if (hd) "id=\"hdlink\"" else "id=\"sdlink\""
            val idx = source.indexOf(start)
            if (idx != -1) {
                val subSource = source.substring(idx + start.length)
                val string = subSource.substring(0, subSource.indexOf(end))
                val regex = "href=\"(.*?)\""
                val pattern = Pattern.compile(regex)
                val matcher = pattern.matcher(string)
                if (matcher.find()) {
                    return matcher.group(1)?.replace("&amp;", "&")
                }
            }
        }
        return null
    }

    fun getThumbnail(source: String?): String? {
        if (source != null) {
            val end = "decoding="
            val start = "class=\"lib-img-show\""
            val idx = source.indexOf(start)
            if (idx != -1) {
                val subSource = source.substring(idx + start.length)
                val string = subSource.substring(0, subSource.indexOf(end))
                val regex = "src=\"(.*?)\""
                val pattern = Pattern.compile(regex)
                val matcher = pattern.matcher(string)
                if (matcher.find()) {
                    return matcher.group(1)?.replace("&amp;", "&")
                }
            }
        }
        return null
    }
}