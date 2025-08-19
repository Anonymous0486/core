package org.app.core.feature.media

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.app.core.base.di.IoDispatcher
import org.app.core.feature.model.media.Album
import org.app.core.feature.model.media.MediaOrder
import org.app.core.feature.model.media.MediaType
import org.app.core.feature.model.media.OrderType
import org.app.core.feature.model.media.Photo
import javax.inject.Inject

class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    @SuppressLint("InlinedApi")
    fun getAlbumsWithType(
        type: MediaType
    ): Flow<Result<List<Album>>> = context.retrieveAlbums {
        val query = Query.AlbumQuery().copy(
            bundle = Bundle().apply {
                val mimeType = when (type) {
                    is MediaType.Photos -> "image%"
                    MediaType.Videos -> "video%"
                    MediaType.Both -> "%/%"
                }
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    MediaStore.MediaColumns.MIME_TYPE + " like ?"
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(mimeType)
                )
            }
        )
        it.getAlbums(query, mediaOrder = MediaOrder.Label(OrderType.Ascending))
    }

    fun mediaFlowWithType(
        albumId: Long,
        type: MediaType
    ): Flow<Result<List<Photo>>> =
        (if (albumId != -1L) {
            getMediaByAlbumIdWithType(albumId, type)
        } else {
            getMediaByType(type)
        }).flowOn(dispatcher).conflate()

    @SuppressLint("InlinedApi")
    fun getMediaByAlbumIdWithType(
        albumId: Long,
        type: MediaType
    ): Flow<Result<List<Photo>>> =
        context.retrieveMedia {
            val query = Query.MediaQuery().copy(
                bundle = Bundle().apply {
                    val mimeType = when (type) {
                        is MediaType.Photos -> "image%"
                        MediaType.Videos -> "video%"
                        MediaType.Both -> "%/%"
                    }
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        MediaStore.MediaColumns.BUCKET_ID + "= ? and " + MediaStore.MediaColumns.MIME_TYPE + " like ?"
                    )
                    putStringArray(
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        arrayOf(albumId.toString(), mimeType)
                    )
                }
            )
            /** return@retrieveMedia */
            it.getPhoto(query)
        }

    fun getMediaByType(type: MediaType): Flow<Result<List<Photo>>> =
        context.retrieveMedia {
            val query = when (type) {
                is MediaType.Photos -> Query.PhotoQuery()
                MediaType.Videos -> Query.VideoQuery()
                MediaType.Both -> Query.MediaQuery()
            }
            it.getPhoto(mediaQuery = query)
        }

    private val uris = arrayOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )

    private fun Context.retrieveMedia(dataBody: suspend (ContentResolver) -> List<Photo>) =
        contentFlowObserver(uris).map {
            try {
                Result.success(dataBody.invoke(contentResolver))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }.conflate()

    private fun Context.retrieveAlbums(dataBody: suspend (ContentResolver) -> List<Album>) =
        contentFlowObserver(uris).map {
            try {
                Result.success(dataBody.invoke(contentResolver))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }.conflate()

}