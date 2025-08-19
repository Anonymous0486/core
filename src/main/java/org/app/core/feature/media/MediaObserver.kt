package org.app.core.feature.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.MergeCursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.app.core.base.utils.DateUtils.Companion.FULL_DATE_TIME_FORMAT_12
import org.app.core.base.utils.secondToSimpleDate
import org.app.core.feature.model.media.MediaOrder
import org.app.core.feature.model.media.OrderType
import org.app.core.feature.model.media.Photo
import timber.log.Timber

private var observerJob: Job? = null

fun Context.contentFlowObserver(uris: Array<Uri>) = callbackFlow {
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = launch(Dispatchers.IO) {
                send(false)
            }
        }
    }
    for (uri in uris)
        contentResolver.registerContentObserver(uri, true, observer)
    // trigger first.
    observerJob = launch(Dispatchers.IO) {
        send(true)
    }
    awaitClose {
        contentResolver.unregisterContentObserver(observer)
    }
}.conflate().onEach { if (!it) delay(1000) }

suspend fun ContentResolver.getPhoto(
    mediaQuery: Query = Query.MediaQuery(),
    mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending)
): List<Photo> {
    return withContext(Dispatchers.IO) {
        val media = ArrayList<Photo>()
        query(mediaQuery).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val photo = cursor.getMediaFromCursor()
                    if (photo != null) {
                        media.add(photo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return@withContext mediaOrder.sortMedia(media)
    }
}


@Throws(Exception::class)
fun Cursor.getMediaFromCursor(): Photo? {
    val id: Long = try {
        getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
    } catch (_: Exception) {
        -1
    }

    val path: String = try {
        getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
    } catch (_: Exception) {
        ""
    }

    val relativePath: String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getString(getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
    val title: String = try {
        getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
    } catch (_: Exception) {
        ""
    }
    val albumID: Long = try {
        getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
    } catch (_: Exception) {
        0
    }

    val albumLabel: String = try {
        getString(getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
    } catch (_: Exception) {
        Build.MODEL
    }
    val takenTimestamp: Long? = try {
        //TODO: For Android 9
//        getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
        null
    } catch (_: Exception) {
        null
    }
    val addedTimestamp: Long = try {
        getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    val duration: String? = try {
        //TODO: For Android 9
//        getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
        ""
    } catch (_: Exception) {
        null
    }
    val mimeType: String =
        getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
    val isFavorite: Int = 0 //getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE))
    val isTrashed: Int = 0 //getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED))

    val expiryTimestamp: Long? = try {
        getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES))
    } catch (_: Exception) {
        null
    }
    val width: Int = try {
        getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
    } catch (_: Exception) {
        0
    }
    val height: Int = try {
        getInt(getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
    } catch (_: Exception) {
        0
    }
    
    val contentUri = if (mimeType.contains("image"))
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    else
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val uri = ContentUris.withAppendedId(contentUri, id)
    val formattedDate = addedTimestamp.secondToSimpleDate(FULL_DATE_TIME_FORMAT_12)
    if (id == -1L || path.isBlank()) {
        return null
    }

    Timber.tag("###DEBUG").e("Found photo $title")
    return Photo(
        id = id,
        label = title,
        uri = uri.toString(),
        path = path,
        relativePath = relativePath,
        albumID = albumID,
        albumLabel = albumLabel,
        timestamp = addedTimestamp,
        takenTimestamp = takenTimestamp,
        expiryTimestamp = expiryTimestamp,
        fullDate = formattedDate,
        duration = duration,
        favorite = isFavorite,
        trashed = isTrashed,
        mimeType = mimeType,
        width = width,
        height = height
    )
}


suspend fun ContentResolver.query(
    mediaQuery: Query
): Cursor = withContext(Dispatchers.IO) {
    MergeCursor(
        arrayOf(
            query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaQuery.projection,
                mediaQuery.bundle,
                null
            ),
            query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                mediaQuery.projection,
                mediaQuery.bundle,
                null
            )
        )
    )
}