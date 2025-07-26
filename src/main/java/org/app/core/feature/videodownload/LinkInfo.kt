package org.app.core.feature.videodownload

import org.app.core.feature.model.LinkResponse

data class LinkInfo(
    val url: String,
    val format: String?,
    val type: String?,
    var size: String? = "",
    var length: Long = 0,
    val label: String = ""
) : Comparable<LinkInfo> {
    constructor(data: LinkResponse) : this(
        url = data.url ?: "",
        format = data.format ?: (data.resolution ?: ""),
        type = data.type ?: data.ext
    )

    override fun compareTo(other: LinkInfo): Int {
        if (label.isNotBlank() || other.label.isNotBlank()) {
            return  if (label.length > other.label.length) -1 else (if (label.length < other.label.length) 1 else (label.compareTo(other.label, true) * -1))
        } else {
            val priorityMap = mapOf(
                "hd" to 2,
                "sd" to 1,
            )
            val priority = format?.let {
                priorityMap.get(format.lowercase()) ?: 0
            }  ?: 0

            val otherPriority = other.format?.let {
                priorityMap.get(it.lowercase()) ?: 0
            } ?: 0

            return  if (priority > otherPriority) -1 else (if (priority < otherPriority) 1 else 0)
        }
    }
}