package org.app.core.feature.datasource

import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DownloadLinkResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TiktokService {
    @GET("api/v1/tiktok/link")
    suspend fun getTtLink(@Query("url") url: String): BaseResponse<DownloadLinkResponse>
}