package org.app.core.feature.datasource

import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.GetLinkResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    
    @GET("api/v3/facebook/download")
    suspend fun getFbLink(@Query("url") url: String): BaseResponse<GetLinkResponse>
    
}