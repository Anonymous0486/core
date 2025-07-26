package org.app.core.ads.base

import androidx.annotation.StringDef

@StringDef(
    CollapsibleType.TOP,
    CollapsibleType.BOTTOM,
    CollapsibleType.NORMAL
)
annotation class CollapsibleType {
    companion object {
        const val TOP = "top"
        const val BOTTOM = "bottom"
        const val NORMAL = "normal"
    }
}