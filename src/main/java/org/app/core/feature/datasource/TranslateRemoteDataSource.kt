package org.app.core.feature.datasource

import com.google.gson.Gson
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.app.core.BuildConfig
import org.app.core.ads.utils.decryptCBC
import org.app.core.feature.model.BaseResponse
import org.app.core.feature.model.DictionaryModel
import org.app.core.feature.model.FailureStatus
import org.app.core.feature.model.ResponseData
import org.app.core.feature.model.TranslateResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class TranslateRemoteDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) : BaseRemoteDataSource() {
    // Network component initialize
    private var _translateRetrofit: Retrofit? = null
    private var _webRetrofit: Retrofit? = null
    private var _translateService: TranslateService? = null
    private var _webService: WebTranslateService? = null
    
    suspend fun translate(content: String, languageCode: String) : ResponseData<BaseResponse<TranslateResponse?>?> {
        if (!translateNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }
        
        return safeApiCall { _translateService!!.translates(content, languageCode) }
    }
    
    suspend fun multipleTranslate(contents: List<String>, languageCode: RequestBody) : ResponseData< BaseResponse<List<String>?>?> {
        if (!translateNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }
        
        val contentTranslate = contents.map { text ->
            MultipartBody.Part.createFormData("content_translate[]", text)
        }
        
        return safeApiCall { _translateService!!.multipleTextTranslates(contentTranslate, languageCode) }
    }
    
    suspend fun translateWebBy(url: String, from: String, to: String) : ResponseData< BaseResponse<String?>?> {
        if (!webTranslateNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }
        
        return safeApiCall { _webService!!.translateWebBy(url, from, to) }
    }
    
    suspend fun dictionary(term: String): ResponseData< BaseResponse<DictionaryModel>?> {
        if (!translateNetworkInitializing()) {
            return ResponseData.Failure(FailureStatus.API_FAIL, 404, "Network component is not ready")
        }
        
        return safeApiCall { _translateService!!.dictionaryGet(term) }
    }
    
    private fun translateNetworkInitializing() : Boolean {
        try {
            if (_translateRetrofit == null) {
                val decryptedUrl = BuildConfig.translateDm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false
                
                _translateRetrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }
            
            if (_translateService == null) {
                _translateService = _translateRetrofit!!.create(TranslateService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }
        
        return true
    }
    
    private fun webTranslateNetworkInitializing() : Boolean {
        try {
            if (_webRetrofit == null) {
                val decryptedUrl = BuildConfig.translateWebDm.decryptCBC(BuildConfig.iv, BuildConfig.secret)
                if (decryptedUrl.isBlank()) return false
                
                _webRetrofit = Retrofit.Builder()
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl(decryptedUrl)
                    .build()
            }
            
            if (_webService == null) {
                _webService = _webRetrofit!!.create(WebTranslateService::class.java)
            }
        }  catch (ex: Exception) {
            return false
        }
        
        return true
    }
}