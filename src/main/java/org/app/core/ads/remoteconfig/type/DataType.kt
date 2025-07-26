package org.app.core.ads.remoteconfig.type

import androidx.annotation.StringDef

@StringDef(
    DataType.LOCAL,
    DataType.REMOTE,
    DataType.UNKNOWN
)
annotation class DataType {
    companion object {
        const val LOCAL = "local"
        const val REMOTE = "remote"
        const val UNKNOWN = "unknown"
    }
}