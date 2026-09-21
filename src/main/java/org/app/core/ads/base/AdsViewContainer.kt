package org.app.core.ads.base

import android.widget.FrameLayout
import android.widget.ImageView
import androidx.cardview.widget.CardView

data class AdsViewContainer(
    var adsContainer: FrameLayout? = null,
    var layoutCard: CardView? = null,
    var nativeFullContainer: FrameLayout? = null,
    var closeNativeFullAds: ImageView? = null
) {
    fun clear() {
        adsContainer?.removeAllViews()
        adsContainer = null
        layoutCard = null
        nativeFullContainer?.removeAllViews()
        nativeFullContainer = null
        closeNativeFullAds = null
    }
}
