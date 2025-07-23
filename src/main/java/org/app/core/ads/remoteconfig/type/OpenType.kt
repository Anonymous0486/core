package org.app.core.ads.remoteconfig.type

import androidx.annotation.StringDef

@StringDef(
    OpenType.INTER,
    OpenType.OPEN
)
annotation class OpenType {
    companion object {
        const val INTER = "inter"
        const val OPEN = "open"
    }
}