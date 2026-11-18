package org.app.core.feature.model

import com.google.gson.annotations.SerializedName

class SyncData(
    @SerializedName("name") var name: String?,
    @SerializedName("is_incoming") var incoming: Boolean?,
    @SerializedName("call_time") var callTime: Long?,
    @SerializedName("duration") var duration: Long?,
    // null or "call" for a call record, "voice" for a voice record (older app versions ignore this field)
    @SerializedName("type") var type: String? = null
) {
    val isVoice: Boolean
        get() = type == TYPE_VOICE

    companion object {
        const val TYPE_VOICE = "voice"
    }
}