package org.app.core.feature.datasource

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DictionaryModel
import org.app.core.feature.model.TextTranslateRequest
import org.app.core.feature.model.TranslateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface TranslateService {

    @POST("api/v2/translate/content")
    suspend fun translates(
        @Query("content_translate") content_translate: String?,
        @Query("language_code") language_code: String?
    ): BaseResponse<TranslateResponse?>?

    @POST("api/v2/translate/content")
    suspend fun translateText(
        @Body request: TextTranslateRequest,
    ): BaseResponse<TranslateResponse?>?

    @POST("api/v2/translate/content-multiple")
    @Multipart
    suspend fun multipleTextTranslates(
        @Part content_translate: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part("language_code") language_code: RequestBody
    ): BaseResponse<List<String>?>?
    
    @GET("api/v2/dictionary/library")
    suspend fun dictionaryGet(
        @Query("term") term: String,
    ): BaseResponse<DictionaryModel>?
}