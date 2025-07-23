package org.app.core.feature.datasource

import org.app.core.feature.model.BaseResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WebTranslateService {
    
    @GET("api/v1/translate/website")
    suspend fun translateWebBy(
        @Query("url") url: String,
        @Query("language_from") language_from: String,
        @Query("language_to") language_to: String
    ): BaseResponse<String?>?
}