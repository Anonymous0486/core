package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class OpenApp {
    @SerializedName("version")
    var version: Int? = null
    
    @SerializedName("status")
    var status: Boolean? = false

    @SerializedName("id")
    var id: String? = null
    
    @SerializedName("event")
    var event: String? = null

    @SerializedName("always_preload")
    var always_preload: Boolean? = false
}