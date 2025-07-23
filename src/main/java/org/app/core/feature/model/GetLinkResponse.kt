package org.app.core.feature.model

data class GetLinkResponse(
    val title: String?,
    val thumbnail: String?,
    val time: String?,
    val links: List<LinkResponse>? = null
)