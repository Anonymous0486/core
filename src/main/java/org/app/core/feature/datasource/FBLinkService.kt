package org.app.core.feature.datasource

import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.GetLinkResponse
import org.app.core.feature.model.InstagramLinkResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface FBLinkService {
    @POST("/api/v1/facebook/download")
    @FormUrlEncoded
    suspend fun getFbLink(
        @Field("url") url: String,
        @Field("cookies_source") cookiesSource: String?
    ): BaseResponse<GetLinkResponse>

    @FormUrlEncoded
    @POST("api/v1/instagram/download")
    suspend fun getIgLink(@Field("url") url: String): BaseResponse<GetLinkResponse>
}