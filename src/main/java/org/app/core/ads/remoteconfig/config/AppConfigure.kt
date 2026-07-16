package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class AppConfigure {
    @SerializedName("msg")
    var dialogMessage: String? = null
    
    @SerializedName("version")
    var actualVersion: Long = 0
    
    @SerializedName("url")
    var updateUrl: String? = null

    @SerializedName("policy")
    var policyUrl: String? = null
    
    @SerializedName("isForce")
    var isForceUpdate: Boolean = false

    @SerializedName("mediaPicker")
    var mediaPicker: Boolean = false
    
    @SerializedName("jsScript")
    var remoteJsScript: String = ""
    
    @SerializedName("isRemoteJs")
    var shouldRunRemoteJs: Boolean = false

    @SerializedName("opApiKey")
    var opApiKey: String? = null
}