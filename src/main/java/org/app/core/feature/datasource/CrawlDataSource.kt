package org.app.core.feature.datasource

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.app.core.BuildConfig
import org.app.core.ads.utils.decryptCBC
import org.app.core.feature.model.FailureStatus
import org.app.core.feature.model.ResponseData
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class CrawlDataSource  @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) : BaseRemoteDataSource() {

    private var _fbRetrofit: Retrofit? = null
    private var _fbService: CrawlService? = null
    private var _twRetrofit: Retrofit? = null
    private var _twService: CrawlService? = null

    suspend fun crawlFDownLinks(url: String) : ResponseData<ResponseBody> {
        if (!initializeNetworkComponent()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }

        return  safeApiCall {
            _fbService!!.crawlFbLink(url)
        }
    }


    private fun initializeNetworkComponent() : Boolean {
        try {
            if (_fbRetrofit == null) {
                val decryptedUrl = BuildConfig.crawldm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false

                _fbRetrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }

            if (_fbService == null) {
                _fbService = _fbRetrofit!!.create(CrawlService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }

        return true
    }

    suspend fun crawlTwLinks(url: String) : ResponseData<ResponseBody> {
        if (_twRetrofit == null) {
            _twRetrofit = Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl("https://twdown.net/")
                .build()
        }

        if (_twService == null) {
            _twService = _twRetrofit!!.create(CrawlService::class.java)
        }

        return  safeApiCall {
            _twService!!.crawlTwLink(url)
        }
    }
}