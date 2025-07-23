package org.app.core.feature.datasource

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import org.app.core.BuildConfig
import org.app.core.ads.utils.decryptCBC
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DownloadLinkResponse
import org.app.core.feature.model.FailureStatus
import org.app.core.feature.model.InstagramLinkResponse
import org.app.core.feature.model.ResponseData
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SocialVideoDataSource @Inject constructor() : BaseRemoteDataSource() {

    private val REQUEST_TIME_OUT: Long = 120

    // Network component initialize
    private var _fRetrofit: Retrofit? = null
    private var _iRetrofit: Retrofit? = null
    private var _tRetrofit: Retrofit? = null
    private var _iService: SocialVideoService? = null
    private var _tService: TiktokService? = null

    suspend fun getInstagramLinks(url: String) :  ResponseData<BaseResponse<InstagramLinkResponse>> {
        if (!iNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }

        return safeApiCall { _iService!!.getIgLink(url) }
    }

    suspend fun getTiktokLinks(url: String) : ResponseData<BaseResponse<DownloadLinkResponse>> {
        if (!tNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }

        return safeApiCall { _tService!!.getTtLink(url) }
    }

    suspend fun getDouyinLinks(url: String) :  ResponseData<BaseResponse<DownloadLinkResponse>> {
        if (!iNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }

        return safeApiCall { _iService!!.getDouyinLink(url) }
    }

    suspend fun getTwitterLinks(url: String) :  ResponseData<BaseResponse<DownloadLinkResponse>> {
        if (!iNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }

        return safeApiCall { _iService!!.getTwitterLink(url) }
    }

    private fun iNetworkInitializing() : Boolean {
        try {
            if (_iRetrofit == null) {
                val okHttpClient = OkHttpClient.Builder()
                    .readTimeout(180, TimeUnit.SECONDS)
                    .connectTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
                    .writeTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
                    .build()

                val gson = GsonBuilder()
                    .setLenient()
                    .serializeNulls()
                    .create()

                val decryptedUrl = BuildConfig.socialdm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false

                _iRetrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }

            if (_iService == null) {
                _iService = _iRetrofit!!.create(SocialVideoService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }

        return true
    }

    private fun tNetworkInitializing() : Boolean {
        try {
            if (_tRetrofit == null) {
//                val logging = HttpLoggingInterceptor()
//                logging.level = HttpLoggingInterceptor.Level.BODY
                val okHttpClient = OkHttpClient.Builder()
                    .readTimeout(180, TimeUnit.SECONDS)
                    .connectTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
                    .writeTimeout(REQUEST_TIME_OUT, TimeUnit.SECONDS)
//                    .addNetworkInterceptor(logging)
                    .build()

                val gson = GsonBuilder()
                    .setLenient()
                    .serializeNulls()
                    .create()

                val decryptedUrl = BuildConfig.ttdm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false

                _tRetrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }

            if (_tService == null) {
                _tService = _tRetrofit!!.create(TiktokService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }

        return true
    }
}