package org.app.core.ads.base

import androidx.annotation.IntDef

@IntDef(
    NativeStyle.BIG_1,
    NativeStyle.BIG_2,
    NativeStyle.BIG_3,
    NativeStyle.BIG_4,
    NativeStyle.BIG_5,
    NativeStyle.BIG_6,
    NativeStyle.BIG_7,
    NativeStyle.BIG_8,
    NativeStyle.BIG_9,
    NativeStyle.BIG_10,
    NativeStyle.BIG_11,
    NativeStyle.BIG_12,
    NativeStyle.BIG_13,
    NativeStyle.BIG_14,
    
    NativeStyle.MEDIUM_21,
    NativeStyle.MEDIUM_22,
    
    NativeStyle.SMALL_41,
    NativeStyle.SMALL_42,
    NativeStyle.SMALL_43,
    NativeStyle.SMALL_44,
    
    NativeStyle.TEMPLATE,

    NativeStyle.FULLSCREEN,
    
    NativeStyle.PRELOAD
)
annotation class NativeStyle {
    companion object {
        const val BIG_1 = 1
        const val BIG_2 = 2
        const val BIG_3 = 3
        const val BIG_4 = 4
        const val BIG_5 = 5
        const val BIG_6 = 6
        const val BIG_7 = 7
        const val BIG_8 = 8
        const val BIG_9 = 9
        const val BIG_10 = 10
        const val BIG_11 = 11
        const val BIG_12 = 12
        const val BIG_13 = 13
        const val BIG_14 = 14

        const val MEDIUM_21 = 21
        const val MEDIUM_22 = 22

        const val SMALL_41 = 41
        const val SMALL_42 = 42
        const val SMALL_43 = 43
        const val SMALL_44 = 44
    
        const val TEMPLATE = 66

        const val FULLSCREEN = 68
        
        const val PRELOAD = -4096
    }
}