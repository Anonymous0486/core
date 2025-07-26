package org.app.core.ads.nativeads

import android.widget.FrameLayout
import org.app.core.R

class CustomAdapterNativeAdViews(
    var isLoading: Boolean,
    var layoutAdId: Int,
    var preloads: Int,
    var listNativeAdView: ArrayList<CustomAdapterNativeAdView> = arrayListOf(),
    var container: FrameLayout? = null
) {
    val noCachedList: List<Int>
        get() = listOf(R.layout.ads_native_big_2)
    
    fun shouldNotCached() = noCachedList.contains(layoutAdId)
}