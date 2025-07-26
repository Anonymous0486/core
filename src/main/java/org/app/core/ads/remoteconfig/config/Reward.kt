package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class Reward {
    @SerializedName("version")
    var version: Int? = null
    
    @SerializedName("status")
    var status: Boolean? = false

    @SerializedName("always_preload")
    var always_preload: Boolean? = true
    
    @SerializedName("id")
    var id: String? = null
    
    @SerializedName("event")
    var event: String? = null

    @SerializedName("description")
    var description: String? = null
}