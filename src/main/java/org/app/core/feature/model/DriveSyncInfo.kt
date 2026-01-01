package org.app.core.feature.model

import com.google.gson.annotations.SerializedName

class DriveSyncInfo {
    @SerializedName("latest_sync")
    var latestSync: Long? = null

    @SerializedName("description")
    var description: String? = null

    @SerializedName("sync_items")
    var items: Array<SyncData>? = null

    init {
        latestSync = System.currentTimeMillis()
        description = "Synced at ${latestSync}"
        items = emptyArray()
    }
}