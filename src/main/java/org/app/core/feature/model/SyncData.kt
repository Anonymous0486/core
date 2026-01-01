package org.app.core.feature.model

import com.google.gson.annotations.SerializedName

class SyncData(
    @SerializedName("name") var name: String?,
    @SerializedName("is_incoming") var incoming: Boolean?,
    @SerializedName("call_time") var callTime: Long?,
    @SerializedName("duration") var duration: Long?
) {

}