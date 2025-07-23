package org.app.core.feature.videodownload

import org.app.core.feature.model.DownloadLinkResponse
import org.app.core.feature.model.InstagramLinkResponse

class DownloadInfo {
    var links: List<LinkInfo>
    var thumbnail: String
    var title: String

    constructor(data: Any) {
        when(data) {
            is DownloadLinkResponse -> {
                this.links = data.links?.map { LinkInfo(it) } ?: listOf()
                this.thumbnail = data.thumbnail ?: ""
                this.title = data.title ?: ""
            }
            is InstagramLinkResponse -> {
                val result = data.response
                this.links = result?.links?.map { LinkInfo(it) } ?: listOf()
                this.thumbnail = result?.thumbnail ?: ""
                this.title = result?.title ?: ""
            }
            else -> {
                this.links = listOf()
                this.thumbnail = ""
                this.title = ""
            }
        }
    }

    constructor(links: List<LinkInfo>, thumbnail: String, title: String) {
        this.links = links
        this.thumbnail = thumbnail
        this.title = title
    }

    fun reorderLink() {
        this.links = this.links.sortedWith(nullsLast(compareByDescending { it.length }))
    }
}