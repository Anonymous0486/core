package org.app.core.ads.remoteconfig.type

import androidx.annotation.StringDef

@StringDef(
    SplashType.INTER,
    SplashType.OPEN
)
annotation class SplashType {
    companion object {
        const val INTER = "inter"
        const val OPEN = "open"
    }
}