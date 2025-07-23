package org.app.core.feature.datasource

import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DownloadLinkResponse
import org.app.core.feature.model.InstagramLinkResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface SocialVideoService {

    @FormUrlEncoded
    @POST("api/v1/instagram/download")
    suspend fun getIgLink(@Field("url") url: String): BaseResponse<InstagramLinkResponse>

    // Douyin and Twitter use same domain as IG
    @FormUrlEncoded
    @POST("api/v1/douyin/download")
    suspend fun getDouyinLink(@Field("url") url: String): BaseResponse<DownloadLinkResponse>

    @FormUrlEncoded
    @POST("api/v1/twitter/download")
    suspend fun getTwitterLink(@Field("url") url: String): BaseResponse<DownloadLinkResponse>
}