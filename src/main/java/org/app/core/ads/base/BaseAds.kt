package org.app.core.ads.base

import android.app.Activity
import org.app.core.ads.callback.AdsCallback
import androidx.annotation.CallSuper
import org.app.core.ads.callback.LoadCallback

abstract class BaseAds<T> protected constructor(
    protected var activity: Activity,
    protected var adId: String
) : Ads() {

    @JvmField
    protected var ads: T? = null
    val isAvailable: Boolean
        get() = ads != null

    // Callback
    private var loadCallback: LoadCallback? = null
    private var adsCallback: AdsCallback? = null

    fun setLoadCallback(callback: LoadCallback?): BaseAds<T> {
        loadCallback = callback
        return this
    }

    fun setAdsCallback(callback: AdsCallback?): BaseAds<T> {
        adsCallback = callback
        return this
    }

    fun clearLoadCallback() {
        loadCallback = null
    }

    fun clearAdsCallback() {
        adsCallback = null
    }

    fun clearAllCallback() {
        clearLoadCallback()
        clearAdsCallback()
    }

    // Auto Reload
    private var autoReload = true
    fun turnOnAutoReload() {
        autoReload = true
    }

    fun turnOffAutoReload() {
        autoReload = false
    }

    // status of ad
    private var isLoading = false
    fun isLoading(): Boolean = isLoading

    private var isShowing = false
    fun isShowing(): Boolean = isShowing


    override fun load(): BaseAds<T>? {
        if (!autoReload) return this

        if (!isAvailable) initAds()
        loadAds()

        return this
    }

    override fun show(callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing) return

        if (isLoading) {
            onShowError("Ads are being loading")
            return
        }

        if (!isAvailable) {
            load()

            onShowError("Ads have not been initialized")
            return
        }

        showAds()
    }

    @CallSuper
    override fun onLoadSuccess() {
        isLoading = false
        loadCallback?.onLoadSuccess()
    }

    @CallSuper
    override fun onLoadFailed(message: String?) {
        isLoading = false
        loadCallback?.onLoadFailed(message)
    }

    @CallSuper
    override fun onShowSuccess() {
        isShowing = true
        adsCallback?.onShow()
    }

    @CallSuper
    override fun onShowError(message: String?) {
        isShowing = false
        isLoading = false
        adsCallback?.onError(message)
    }

    @CallSuper
    override fun onClosed() {
        isShowing = false
        adsCallback?.onClosed()
        destroyAds()
        load()
    }

    @CallSuper
    override fun onUserRewarded(amount: Int, type: String) {
        adsCallback?.getReward(amount = amount, type = type)
    }

    open fun initAds() {}
    open fun loadAds() {
        isLoading = true
    }

    open fun showAds() {}
    open fun destroyAds() {}
}