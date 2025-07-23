package org.app.core.ads.remoteconfig.type

import androidx.annotation.StringDef

@StringDef(
    MediationType.ADMOB,
    MediationType.MAX,
    MediationType.UNKNOWN
)
annotation class MediationType {
    companion object {
        const val ADMOB = "admob"
        const val MAX = "max"
        const val UNKNOWN = "unknown"
    }
}