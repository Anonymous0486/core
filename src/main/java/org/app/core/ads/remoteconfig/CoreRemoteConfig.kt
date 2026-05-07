package org.app.core.ads.remoteconfig

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.app.core.ads.CoreAds
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.config.AdsConfigure
import org.app.core.ads.remoteconfig.config.AppConfigure
import org.app.core.ads.remoteconfig.config.BackupAds
import org.app.core.ads.remoteconfig.config.OpenApp
import org.app.core.ads.remoteconfig.type.DataType
import org.app.core.ads.remoteconfig.type.MediationType
import org.app.core.feature.extension.coroutinesIO
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

@SuppressLint("LogNotTimber")
class CoreRemoteConfig {
    private var fetchTime = 3600L
    private var keyAdsConfig = "config_ads"
    private var nameFileRemoteConfigAdsLocal = "config_ads_default"
    private var keyAppConfig = "config_app"
    private var isDataType: String = MediationType.UNKNOWN
    private var isSetting = false
    private var isSetupSuccess = false
    private var _appRemoteConfig: AppConfigure? = null
    private var _adsRemoteConfig: AdsConfigure? = null
    
    val appRemoteConfig: AppConfigure?
        get() = _appRemoteConfig
    
    val adsRemoteConfig: AdsConfigure?
        get() = _adsRemoteConfig
    
    fun setReleaseFetchTime(fetchTime: Long) {
        this.fetchTime = fetchTime
    }

    fun setKeyConfig(keyConfig: String) {
        this.keyAdsConfig = keyConfig
    }

    fun setNameFileRemoteConfigAdsLocal(nameFileRemoteConfigAdsLocal: String) {
        this.nameFileRemoteConfigAdsLocal = nameFileRemoteConfigAdsLocal
    }
    
    fun findAppOpenId() : String? {
        _adsRemoteConfig?.let { config ->
            val filter = config.open_ads?.filter { it.status == true }
            
            return filter?.firstOrNull()?.id
        }
        
        return null
    }

    fun findAppOpenAds() : OpenApp? {
        _adsRemoteConfig?.let { config ->
            return config.open_ads?.firstOrNull()
        }

        return null
    }

    fun isSetupSuccess(): Boolean {
        return isSetupSuccess
    }

    @Suppress("OPT_IN_USAGE")
    fun init(
        activity: Activity,
        isLocal: Boolean = false,
        callback: LoadCallback? = null,
    ) {
        if (!CoreAds.isNetworkAvailable(activity) || isLocal) {
            isSetupSuccess = false
            isSetting = false
            isDataType = DataType.LOCAL
            coroutinesIO {
                setupLocalDataAdsRemoteConfig(activity, callback)
            }
            return
        }

        if (isSetupSuccess) {
            callback?.onLoadFailed("Task has been setup")
            return
        }
        
        if (isSetting) {
            callback?.onLoadFailed("Task is setting")
            return
        }

        isSetting = true
        val config = Firebase.remoteConfig
        //TODO: Should only use for development (not production)
//        val configSettings = remoteConfigSettings {
//            minimumFetchIntervalInSeconds = 3600
//        }
//        config.setConfigSettingsAsync(configSettings)

        config.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Timber.tag(TAG).i("OnConfigUpdate: ${configUpdate.updatedKeys}")
                config.activate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        handleRemoteConfig(config)
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Timber.tag(TAG).i("Config update error with code: ${error.code} - ${error.message}")
            }
        })

        config.fetch().addOnCompleteListener { task ->
            if (!task.isComplete || !task.isSuccessful) {
                Timber.tag(TAG).i("Remote: Task is not successful")
                GlobalScope.launch(Dispatchers.IO) {
                    setupLocalDataAdsRemoteConfig(activity, callback)
                }
            } else {
                config.activate().addOnCompleteListener { activateTask ->
                    GlobalScope.launch(Dispatchers.IO) {
                        if (activateTask.isSuccessful) {
                            try {
                                handleRemoteConfig(config)
                                withContext(Dispatchers.Main) {
                                    Timber.tag(TAG).i("Remote: Task is successful")
                                    callback?.onLoadSuccess()
                                }
                            } catch (e: Exception) {
                                Timber.tag(TAG).i("Remote Exception: ${e.message}")
                                setupLocalDataAdsRemoteConfig(activity, callback)
                            }
                        } else {
                            setupLocalDataAdsRemoteConfig(activity, callback)
                        }
                    }
                }
            }
        }
    }

    private fun handleRemoteConfig(config: FirebaseRemoteConfig) {
        val adsCfgJson = config.getString(keyAdsConfig)
        _adsRemoteConfig = Gson().fromJson(adsCfgJson, AdsConfigure::class.java)

        Timber.tag(TAG).i("Remote: active version: ${_adsRemoteConfig?.active_version} - ${_adsRemoteConfig?.status}")
        val appCfgJson = config.getString(keyAppConfig)
        _appRemoteConfig = Gson().fromJson(appCfgJson, AppConfigure::class.java)

        Timber.tag(TAG).i("Remote: actual version: ${_appRemoteConfig?.actualVersion} - ${_appRemoteConfig?.isForceUpdate}")
        isSetupSuccess = true
        isSetting = false
        isDataType = DataType.REMOTE
    }
    
    fun setLocalConfig(jsonString: String, forceLocal: Boolean = false) {
        try {
            if (_adsRemoteConfig == null || forceLocal) {
                _adsRemoteConfig = Gson().fromJson(jsonString, AdsConfigure::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchLocalConfig(activity: Activity) : AdsConfigure? {
        val json = readFileRemoteConfigAdsDefaultLocal(activity)
        if (json.isNotEmpty()) {
            try {
                _adsRemoteConfig = Gson().fromJson(json, AdsConfigure::class.java)
                isSetupSuccess = true
                isSetting = false
                isDataType = DataType.LOCAL
                return _adsRemoteConfig
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return null
    }

    fun checkForceUpdateIfNeed(currentVersion: Int) : Boolean {
        _appRemoteConfig?.let { config ->
            if (currentVersion < config.actualVersion && config.isForceUpdate) {
                Log.i(TAG, "checkForceUpdateIfNeed: ---> True")
                return true
            }
        }
        
        Log.i(TAG, "checkForceUpdateIfNeed: ---> False $currentVersion - ${_appRemoteConfig?.actualVersion}")
        return false
    }

    fun getPolicyLink() : String {
        return _appRemoteConfig?.policyUrl ?: "https://merryblue.llc/policy"
    }

    fun getBackupAds(type: String) : List<BackupAds> {
        return _adsRemoteConfig?.backups?.filter { it.type == type } ?: emptyList()
    }
    
    private suspend fun setupLocalDataAdsRemoteConfig(
        activity: Activity,
        callback: LoadCallback?
    ) {
        var localResult = ""//readLocalDataAdsRemoteConfig(activity)
        if (localResult.isEmpty()) localResult = readFileRemoteConfigAdsDefaultLocal(activity)
        Timber.tag(TAG).i("Remote: setup local data")
        if (localResult.isNotEmpty()) {
            try {
                _adsRemoteConfig = Gson().fromJson(localResult, AdsConfigure::class.java)
                isSetupSuccess = true
                isSetting = false
                isDataType = DataType.LOCAL
                withContext(Dispatchers.Main) {
                    Timber.tag(TAG).i("Local: Task is successful")
                    callback?.onLoadSuccess()
                }
                return
            } catch (e: Exception) {
                Timber.tag(TAG).i("Local Exception: ${e.message}")
                isSetupSuccess = false
                isSetting = false
                withContext(Dispatchers.Main) {
                    callback?.onLoadFailed(e.message)
                }
                return
            }
        }
        isSetupSuccess = false
        isSetting = false
        isDataType = DataType.UNKNOWN
        withContext(Dispatchers.Main) {
            Timber.tag(TAG).i("Local: Empty data")
            callback?.onLoadFailed("Local: Empty data")
        }
        
        return
    }

    private fun readLocalDataAdsRemoteConfig(activity: Activity): String {
        val file = File(activity.filesDir, NAME_LOCAL_DATA_ADS_REMOTE_CONFIG)
        val created = file.createNewFile()
        if (created) return ""
        var text: String
        FileInputStream(file).use { inpS ->
            inpS.bufferedReader().use {
                text = it.readText()
                it.close()
            }
            inpS.close()
        }
        return String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun writeLocalDataAdsRemoteConfig(activity: Activity, data: String) {
        val file = File(activity.filesDir, NAME_LOCAL_DATA_ADS_REMOTE_CONFIG)
        file.createNewFile()
        file.writeText(Base64.encodeToString(data.toByteArray(), Base64.DEFAULT))
    }

    private fun readFileRemoteConfigAdsDefaultLocal(activity: Activity): String {
        try {
            val ins = activity.resources.openRawResource(
                activity.resources.getIdentifier(
                    nameFileRemoteConfigAdsLocal,
                    "raw", activity.packageName
                )
            )
            var text: String
            ins.bufferedReader().use {
                text = it.readText()
                it.close()
            }
            ins.close()
            return text
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    companion object {
        private const val TAG = "CoreRemoteConfig"
        private const val NAME_LOCAL_DATA_ADS_REMOTE_CONFIG =
            ".local_data_ads_remote_config.txt"

        private var INSTANCE: CoreRemoteConfig? = null

        @JvmStatic
        val instance: CoreRemoteConfig
            get() {
                if (INSTANCE == null) {
                    synchronized(CoreRemoteConfig::class.java) {
                        if (INSTANCE == null) {
                            INSTANCE = CoreRemoteConfig()
                        }
                    }
                }
                return INSTANCE!!
            }
    }
}