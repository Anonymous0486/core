package org.app.core.feature.model.media

data class Photo(
    val id: Long = 0,
    val label: String,
    val uri: String,
    val path: String,
    val relativePath: String,
    val albumID: Long,
    val albumLabel: String,
    val timestamp: Long,
    val expiryTimestamp: Long? = null,
    val takenTimestamp: Long? = null,
    val fullDate: String,
    val mimeType: String,
    val favorite: Int,
    val trashed: Int,
    val duration: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    val isVideo: Boolean = mimeType.startsWith("video/")
    
    val isImage: Boolean = mimeType.startsWith("image/")
    
    val isTrashed: Boolean = trashed == 1
    
    val isFavorite: Boolean = favorite == 1
    
    val readUriOnly: Boolean = albumID == -99L && albumLabel == ""
    
    val isRaw: Boolean =
        mimeType.isNotBlank() && (mimeType.startsWith("image/x-") || mimeType.startsWith("image/vnd."))
    
    val fileExtension: String = label.substringAfterLast(".").removePrefix(".")
    
    val volume: String = path.substringBeforeLast("/").removeSuffix(relativePath.removeSuffix("/"))
    
    companion object {
        fun dummy() = Photo(
            -1,
            "",
            "",
            "",
            "",
            -1L,
            "",
            0,
            0,
            0,
            "",
            "",
            0,
            0,
            null,
            0,
            0
        )
    }
}