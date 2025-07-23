package org.app.core.feature.model

data class DownloadLinkResponse(
    val title: String?,
    val thumbnail: String?,
    val time: String?,
    val source: String?,
    val links: List<LinkResponse>? = null
)