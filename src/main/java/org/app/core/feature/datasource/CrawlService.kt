package org.app.core.feature.datasource

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface CrawlService {
    @FormUrlEncoded
    @POST("download.php")
    suspend fun crawlFbLink(
        @Field("URLz") url: String,
        @Header("User-agent") agent: String = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.99 Safari/537.36"
    ): ResponseBody

    @FormUrlEncoded
    @POST("download.php")
    suspend fun crawlTwLink(
        @Field("URL") url: String,
    ): ResponseBody
}