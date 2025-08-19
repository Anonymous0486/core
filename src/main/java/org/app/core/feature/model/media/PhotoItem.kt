package org.app.core.feature.model.media

sealed class PhotoItem {

    abstract val key: String

    data class Header(
        override val key: String,
        val text: String,
    ) : PhotoItem()

    data class MediaViewItem(
        override val key: String,
        val photo: Photo
    ) : PhotoItem()
}

val Any.isHeaderKey: Boolean
    get() = this is String && this.startsWith("header_")

val Any.isBigHeaderKey: Boolean
    get() = this is String && this.startsWith("header_big_")

val Any.isIgnoredKey: Boolean
    get() = this is String && this == "aboveGrid"