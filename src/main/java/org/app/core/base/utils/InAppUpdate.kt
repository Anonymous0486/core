package org.app.core.base.utils

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson

class InAppUpdate {
    private var remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
    private var remoteConfigState = RemoteConfigurationState.NONE
    var _remoteConfiguration: RemoteConfigurationModel? = null

    val loadedRemoteConfiguration: RemoteConfigurationModel?
        get() = _remoteConfiguration

    init {
        remoteConfigState = RemoteConfigurationState.LOADING
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        // TODO: Check more if need to use default from local
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    try {
                        val json = remoteConfig.getString(KEY_APP_CONFIG)
                        _remoteConfiguration = Gson().fromJson(json, RemoteConfigurationModel::class.java)
                        remoteConfigState = RemoteConfigurationState.LOADED
                    } catch (_: Exception) { }
                    
                    if (_remoteConfiguration == null) {
                        _remoteConfiguration = RemoteConfigurationModel(
                            remoteConfig.getString(KEY_DIALOG_DESCRIPTION),
                            remoteConfig.getLong(KEY_LATEST_VERSION),
                            remoteConfig.getString(KEY_UPDATE_URL),
                            remoteConfig.getBoolean(KEY_IS_FORCE_UPDATE),
                            remoteConfig.getString(KEY_APP_DN_DATA),
                            remoteConfig.getString(KEY_APP_IV_DATA),
                            remoteConfig.getString(KEY_APP_KY_DATA),
                            remoteConfig.getString(KEY_APP_DN_WEB_DATA),
                        )
                        remoteConfigState = RemoteConfigurationState.LOADED
                    }
                } else {
                    remoteConfigState = RemoteConfigurationState.FAILED
                }
            }
    }
    
    fun reloadConfigure() {
        if (_remoteConfiguration == null) {
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        try {
                            val json = remoteConfig.getString(KEY_APP_CONFIG)
                            _remoteConfiguration = Gson().fromJson(json, RemoteConfigurationModel::class.java)
                            remoteConfigState = RemoteConfigurationState.LOADED
                        } catch (_: Exception) { }
                        
                        if (_remoteConfiguration == null) {
                            _remoteConfiguration = RemoteConfigurationModel(
                                remoteConfig.getString(KEY_DIALOG_DESCRIPTION),
                                remoteConfig.getLong(KEY_LATEST_VERSION),
                                remoteConfig.getString(KEY_UPDATE_URL),
                                remoteConfig.getBoolean(KEY_IS_FORCE_UPDATE),
                                remoteConfig.getString(KEY_APP_DN_DATA),
                                remoteConfig.getString(KEY_APP_IV_DATA),
                                remoteConfig.getString(KEY_APP_KY_DATA),
                                remoteConfig.getString(KEY_APP_DN_WEB_DATA),
                            )
                            remoteConfigState = RemoteConfigurationState.LOADED
                        }
                    } else {
                        remoteConfigState = RemoteConfigurationState.FAILED
                    }
                }
        }
    }

    fun checkForceUpdateIfNeed(currentVersion: Int) : Boolean {
        _remoteConfiguration?.let { config ->
            if (currentVersion < config.actualVersion && config.isForceUpdate) {
                Log.i(TAG, "checkForceUpdateIfNeed: ---> True")
                return true
            }
        }

        Log.i(TAG, "checkForceUpdateIfNeed: ---> False")
        return false
    }

    companion object {
        private val TAG = InAppUpdate::class.java.simpleName

        val KEY_LATEST_VERSION = "android_current_version_app"
        val KEY_IS_FORCE_UPDATE = "is_force_update"
        val KEY_UPDATE_URL = "application_store_url"
        val KEY_DIALOG_DESCRIPTION = "force_dialog_description"
        val KEY_APP_DN_DATA = "app_dn_data"
        val KEY_APP_IV_DATA = "app_iv_data"
        val KEY_APP_KY_DATA = "app_ky_data"
        val KEY_APP_DN_WEB_DATA = "app_dn_web_data"
        val KEY_APP_CONFIG = "app_config_key"

        private var sharedInstance: InAppUpdate? = null

        @JvmStatic
        fun initialize() {
            if (sharedInstance == null) {
                sharedInstance = InAppUpdate()
            }
        }

        @JvmStatic
        fun getInstance() = sharedInstance ?: synchronized(this) {
            sharedInstance ?: InAppUpdate().also { sharedInstance = it }
        }
    }

    data class RemoteConfigurationModel(
        val dialogMessage: String,
        val actualVersion: Long,
        val updateUrl: String,
        val isForceUpdate: Boolean,
        val domain: String,
        val encIV: String,
        val encKey: String,
        val domainWeb: String,
    )

    enum class RemoteConfigurationState {
        NONE,
        LOADING,
        LOADED,
        FAILED
    }
}