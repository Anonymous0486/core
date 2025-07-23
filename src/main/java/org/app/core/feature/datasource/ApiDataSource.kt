package org.app.core.feature.datasource

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.app.core.BuildConfig
import org.app.core.ads.utils.decryptCBC
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.FailureStatus
import org.app.core.feature.model.GetLinkResponse
import org.app.core.feature.model.ResponseData
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class ApiDataSource  @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) : BaseRemoteDataSource() {
    
    private val TAG = "##DEBUG: ApiDataSource"
    
    // Network component initialize
    private var _retrofit: Retrofit? = null
    private var _linkService: ApiService? = null
    
    suspend fun getLinks(url: String) : ResponseData<BaseResponse<GetLinkResponse>> {
        if (!initializeNetworkComponent()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }
        
        return  safeApiCall {
            _linkService!!.getFbLink(url)
        }
    }
    
    private fun initializeNetworkComponent() : Boolean {
        try {
            if (_retrofit == null) {
                val decryptedUrl = BuildConfig.fbV1Dm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false
                
                _retrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }
    
            if (_linkService == null) {
                _linkService = _retrofit!!.create(ApiService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }
        
        return true
    }
}