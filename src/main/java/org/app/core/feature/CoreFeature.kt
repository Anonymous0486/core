package org.app.core.feature

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.app.core.BuildConfig
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.utils.decryptCBC
import org.app.core.feature.extension.coroutinesIO
import org.app.core.feature.model.ActionModel
import org.app.core.feature.model.CountryModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

class CoreFeature private constructor() {
    private var _isSafeArea: Boolean = false
    private val _fbCore: FacebookCoreInterface by lazy { FacebookCore() }
    private val _actionModel = mutableMapOf<String, ActionModel?>()
    
    internal val isSafeArea: Boolean
        get() = _isSafeArea

    fun initializeFeatureBy(appId: String) {
        coroutinesIO {
            appFeature(appId)
            getActionInfo()?.let {
                _actionModel.putAll(it)
            }
        }
    }
    
    // Public Common
    fun isForceUpdate(versionCode: Int) : Boolean {
        if (CoreRemoteConfig.instance.isSetupSuccess()) {
            val config = CoreRemoteConfig.instance.appRemoteConfig
            config?.let {
                Log.i(TAG, "isForceUpdate version: ${config.actualVersion}")

                if (versionCode < config.actualVersion && config.isForceUpdate) {
                    Log.i(TAG, "isForceUpdate: ---> True: ${config.actualVersion}")
                    return true
                }
            }
        }
        Log.i(TAG, "isForceUpdate: ---> False")
        return false
    }
    
    //internal
    fun appFeature(appId: String) {
        try {
            val response = getJsonDataFromUrl("http://ip-api.com/json") ?: return
    
            val country = Gson().fromJson(response, CountryModel::class.java)
//            Log.d(TAG, "###ABC: appFeature: $country")
            _isSafeArea = !country.countryCode.equals("vn", true)
        } catch (_: Exception) {  }
    }
    
    // Public Facebook
    fun mediaPostProcessing(
        context: Context,
        videoPath: String,
        audioPath: String,
        outputPath: String,
        workerId: Long) = _fbCore.mergeMedia(context, videoPath, audioPath, outputPath, workerId)

    fun getDomParseJs() = _fbCore.getDomParseJs()

    fun getFBVideoParseJs() = _fbCore.getFBVideoParseJs()

    fun getStoryParseJs() = _fbCore.getStoryParseJs()

    // Private
    private fun getJsonDataFromUrl(url: String): String? {
        try {
            val connection = URL(url).openConnection()
            val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
            val jsonData = StringBuilder()
        
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                jsonData.append(line)
            }
            reader.close()
            return jsonData.toString().ifBlank { null }
        } catch (_: Exception) {  }
    
        return null
    }
    
    private fun getActionInfo() : Map<String, ActionModel?>? {
        try {
            val response = getJsonDataFromUrl("https://raw.githubusercontent.com/thanhmamce/Privacy_Policy/main/action_model")
            response?.let {
                val gson = Gson()
                val mapAdapter = gson.getAdapter(object: TypeToken<Map<String, ActionModel?>>() {})
                val actionModel = mapAdapter.fromJson(response)
//                Log.d(TAG, "###ABC: getActionInfor: $actionModel")
    
                return actionModel
            }
        } catch (_: Exception) { }
        
        return null
    }
    
    companion object {
        private const val TAG = "CoreFeature"
        
        private var INSTANCE: CoreFeature? = null
        
        @JvmStatic
        val instance: CoreFeature
            get() {
                if (INSTANCE == null) {
                    synchronized(CoreFeature::class.java) {
                        if (INSTANCE == null) {
                            INSTANCE = CoreFeature()
                        }
                    }
                }
                return INSTANCE!!
            }
        
        @JvmStatic
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        }
    }
}