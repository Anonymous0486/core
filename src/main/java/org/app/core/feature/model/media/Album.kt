package org.app.core.feature.model.media

data class Album(
    val id: Long = 0,
    val label: String,
    val uri: String,
    val pathToThumbnail: String,
    val relativePath: String,
    val timestamp: Long,
    var count: Long = 0,
    val selected: Boolean = false,
    val isPinned: Boolean = false,
) {
    var photos = arrayListOf<Photo>()
}
