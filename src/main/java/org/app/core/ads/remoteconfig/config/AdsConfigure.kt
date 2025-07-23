package org.app.core.ads.remoteconfig.config

import com.google.gson.annotations.SerializedName

class AdsConfigure {
    @SerializedName("active_version")
    var active_version: Int? = null
    
    @SerializedName("status")
    var status: Boolean? = false

    @SerializedName("open_ads")
    var open_ads: Array<OpenApp>? = null

    @SerializedName("splash")
    var splash: Array<Interstitial>? = null

    @SerializedName("banners")
    var banners: Array<Banner>? = null

    @SerializedName("MRECs")
    var mrecs: Array<MRECs>? = null

    @SerializedName("interstitials")
    var interstitials: Array<Interstitial>? = null

    @SerializedName("natives")
    var natives: Array<Native>? = null
    
    @SerializedName("rewards")
    var rewards: Array<Reward>? = null

    @SerializedName("description")
    var description: String? = null

    @SerializedName("backup_id")
    var backups: Array<BackupAds>? = null
}