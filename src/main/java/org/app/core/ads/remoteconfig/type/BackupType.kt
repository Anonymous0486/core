package org.app.core.ads.remoteconfig.type

import androidx.annotation.StringDef

@StringDef(
    BackupType.NATIVE,
    BackupType.BANNER,
    BackupType.INTERSTITIAL
)
annotation class BackupType {
    companion object {
        const val NATIVE = "native"
        const val BANNER = "banner"
        const val INTERSTITIAL = "interstitial"
    }
}