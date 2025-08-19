package org.app.core.feature.media

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.app.core.base.utils.DateUtils.Companion.EXTENDED_DATE_FORMAT
import org.app.core.base.utils.secondToSimpleDate
import org.app.core.feature.model.media.Album
import org.app.core.feature.model.media.MediaOrder
import org.app.core.feature.model.media.OrderType
import org.app.core.feature.model.media.Photo
import java.io.File
import kotlin.random.Random

sealed class Query(
    var projection: Array<String>,
    var bundle: Bundle? = null
) {
    class MediaQuery : Query(
        projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.DATE_MODIFIED,
//            MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
//            MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.MIME_TYPE,
//            MediaStore.MediaColumns.IS_FAVORITE,
//            MediaStore.MediaColumns.IS_TRASHED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
        }
    )

    @SuppressLint("InlinedApi")
    class TrashQuery : Query(
        projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED
        ),
        bundle = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_EXPIRES)
            )
            putInt(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
    )

    @SuppressLint("InlinedApi")
    class PhotoQuery : Query(
        projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED
        ),
        bundle = defaultBundle.apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.MediaColumns.MIME_TYPE + " like ?"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf("image%")
            )
        }
    )

    @SuppressLint("InlinedApi")
    class VideoQuery : Query(
        projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED
        ),
        bundle = defaultBundle.apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.MediaColumns.MIME_TYPE + " LIKE ?"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf("video%")
            )
        }
    )

    class AlbumQuery : Query(
        projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN
        )
    )

    fun copy(
        projection: Array<String> = this.projection,
        bundle: Bundle? = this.bundle,
    ): Query {
        this.projection = projection
        this.bundle = bundle
        return this
    }

    companion object {
        @SuppressLint("InlinedApi")
        val defaultBundle = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }
    }

}

@SuppressLint("InlinedApi")
suspend fun ContentResolver.getAlbums(
    query: Query = Query.AlbumQuery(),
    mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending)
): List<Album> {
    return withContext(Dispatchers.IO) {
        val timeStart = System.currentTimeMillis()
        val albums = ArrayList<Album>()
        val bundle = query.bundle ?: Bundle()
        val albumQuery = query.copy(
            bundle = bundle.apply {
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
                )
            },
        )
        query(albumQuery).use {
            with(it) {
                while (moveToNext()) {
                    try {
                        val albumId =
                            getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                        val id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val label: String? = try {
                            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
                        } catch (e: Exception) {
                            Build.MODEL
                        }
                        val thumbnailPath =
                            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                        val thumbnailRelativePath =
                            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
                        val thumbnailDate =
                            getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                        val mimeType =
                            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                        val contentUri = if (mimeType.contains("image"))
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        else
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        val album = Album(
                            id = albumId,
                            label = label ?: Build.MODEL,
                            uri = ContentUris.withAppendedId(contentUri, id).toString(),
                            pathToThumbnail = thumbnailPath,
                            relativePath = thumbnailRelativePath,
                            timestamp = thumbnailDate,
                            count = 1
                        )
                        val currentAlbum = albums.find { albm -> albm.id == albumId }
                        if (currentAlbum == null)
                            albums.add(album)
                        else {
                            val i = albums.indexOf(currentAlbum)
                            albums[i].count++
                            if (albums[i].timestamp <= thumbnailDate) {
                                album.count = albums[i].count
                                albums[i] = album
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return@withContext mediaOrder.sortAlbums(albums).also {
            println("Album parsing took: ${System.currentTimeMillis() - timeStart}ms")
        }
    }
}

fun mediaFromUri(
    context: Context,
    uri: Uri
): Photo? {
    if (uri.path == null) return null
    val extension = uri.toString().substringAfterLast(".")
    var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).toString()
    var duration: String? = null
    try {
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(context, uri)
        }
        val hasVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        val isVideo = "yes" == hasVideo
        if (isVideo) {
            duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        }
        if (mimeType.isEmpty()) {
            mimeType = if (isVideo) "video/*" else "image/*"
        }
    } catch (_: Exception) {
    }
    var timestamp = 0L
    uri.path?.let { File(it) }?.let {
        timestamp = try {
            it.lastModified()
        } catch (_: Exception) {
            0L
        }
    }
    var formattedDate = ""
    if (timestamp != 0L) {
        formattedDate = timestamp.secondToSimpleDate(EXTENDED_DATE_FORMAT)
    }
    return Photo(
        id = Random(System.currentTimeMillis()).nextLong(-1000, 25600000),
        label = uri.toString().substringAfterLast("/"),
        uri = uri.toString(),
        path = uri.path.toString(),
        relativePath = uri.path.toString().substringBeforeLast("/"),
        albumID = -99L,
        albumLabel = "",
        timestamp = timestamp,
        fullDate = formattedDate,
        mimeType = mimeType,
        duration = duration,
        favorite = 0,
        trashed = 0
    )
}