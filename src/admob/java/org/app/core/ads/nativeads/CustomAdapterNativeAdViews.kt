package org.app.core.ads.nativeads

import java.util.concurrent.LinkedBlockingQueue

class CustomAdapterNativeAdViews(
    var isLoading: Boolean,
    var preloads: Int,
    var size: Int,
    var nativeAds: LinkedBlockingQueue<AdapterNativeAdView> = LinkedBlockingQueue<AdapterNativeAdView>(),
    var isOnce: Boolean = false,
    var lastDisplayAds: AdapterNativeAdView? = null
)