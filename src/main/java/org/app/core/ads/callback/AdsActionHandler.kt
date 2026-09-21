package org.app.core.ads.callback

interface AdsActionHandler {
    fun showAds(showLoading: Boolean = false): Boolean
    fun refreshNative()
    fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?)
    fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?)
    fun preloadAds()
}