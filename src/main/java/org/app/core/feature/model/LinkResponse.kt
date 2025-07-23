package org.app.core.feature.model

data class LinkResponse(
    val url: String?,
    val format: String?,
    val type: String?,
    var size: String?,
    val ext: String?,
    val resolution: String?
)