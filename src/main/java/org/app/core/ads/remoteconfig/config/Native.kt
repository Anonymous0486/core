package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class Native {
    @SerializedName("version")
    var version: Int? = null
    
    @SerializedName("status")
    var status: Boolean? = false
    
    @SerializedName("place_preload")
    var place_preload: String? = null
    
    @SerializedName("always_preload")
    var always_preload: Boolean? = true
    
    @SerializedName("tag")
    var tag: String? = null
    
    @SerializedName("id")
    var id: String? = null
    
    @SerializedName("event")
    var event: String? = null

    @SerializedName("style")
    var style: Int? = null

    @SerializedName("preload")
    var preload: Int? = null

    @SerializedName("description")
    var description: String? = null
}