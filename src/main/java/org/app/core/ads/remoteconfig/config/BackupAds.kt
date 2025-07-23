package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class BackupAds {
    @SerializedName("id")
    var id: String? = null

    @SerializedName("network")
    var network: String? = null

    @SerializedName("type")
    var type: String? = null        //Native, Banner, Interstitial

    var order: Int? = null
}