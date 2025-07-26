package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class MRECs {
    @SerializedName("version")
    var version: Int? = null
    
    @SerializedName("status")
    var status: Boolean? = false
    
    @SerializedName("id")
    var id: String? = null
    
    @SerializedName("event")
    var event: String? = null

    @SerializedName("description")
    var description: String? = null
}