package org.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import android.view.ViewGroup
import com.applovin.sdk.AppLovinSdkUtils
import com.google.android.gms.ads.AdSize
import org.app.core.ads.base.BannerAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.openads.AdapterOpenAppManager

class AdapterBannerAds(
    activity: Activity,
    container: FrameLayout?,
    adId: String,
    private val eventId: String,
    var adsSize: AdSize? = null,
    var collapsibleType: String? = null,
    var isShowAdsWhenLoaded: Boolean = true
) : BannerAds<MaxAdView?>(activity, container, adId) {
    
    private val tag = "AdapterBannerAds"
    
    override fun initAds() {
        super.initAds()
        ads = MaxAdView(adId, activity.applicationContext)
        ads?.layoutParams = adSizeDefault
        initAdListener()
    }

    private fun initAdListener() {
        ads?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d(tag, "-------------------------------------------")
            Log.d(tag, "BannerMax Revenue: " + ad.revenue)
            Log.d(tag, "BannerMax NetworkName: " + ad.networkName)
            Log.d(tag, "BannerMax AdUnitId: " + ad.adUnitId)
            Log.d(tag, "BannerMax Placement: " + ad.placement)
            Log.d(tag, "-------------------------------------------")
        }

        ads?.setListener(object : MaxAdViewAdListener {
            override fun onAdExpanded(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdExpanded")
            }

            override fun onAdCollapsed(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdCollapsed")
            }

            override fun onAdLoaded(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdLoaded $adId")
                onLoadSuccess()
    
                if (isShowAdsWhenLoaded) {
                    container?.visibility = View.VISIBLE
                }
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(tag, "BannerMax onAdLoadFailed:  $adId -> ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdDisplayed  $adId")
                onShowSuccess()

                if (shimmer != null) {
                    container?.removeView(shimmer)
                    shimmer = null
                }
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(tag, "BannerMax onAdDisplayFailed:  $adId -> ${error.message}")
                onShowError(error.message)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdHidden")
                onClosed()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(tag, "BannerMax onAdClicked")
                AdapterOpenAppManager.isAdOtherClicked = true
    
                val param = Bundle().apply {
                    this.putString("eventType", "Clicked")
                }
                CoreAds.instance.logFirebaseEvent(eventId, param)
            }
        })
    }

    override fun loadAds() {
        super.loadAds()
        ads?.loadAd()
        turnOnAutoReload()
    }

    override fun showAds() {
        super.showAds()
        container?.addView(ads)
        ads?.startAutoRefresh()
    }
    
    fun show(container: FrameLayout, callback: AdsCallback?) {
        if (ads?.parent != null) {
            (ads?.parent as FrameLayout?)?.removeView(ads)
        }
        
        if (this.container != null) {
            this.container?.removeAllViews()
        }
        
        setAdsCallback(callback)
        this.container = container
        container.removeAllViews()
        container.addView(ads)
        ads?.startAutoRefresh()
    }

    override fun destroyAds() {
        super.destroyAds()
        ads?.destroy()
        ads = null
    }

    fun startAutoRefresh() {
        ads?.startAutoRefresh()
    }

    fun stopAutoRefresh() {
        ads?.stopAutoRefresh()
    }

    // Set the height of the banner ad based on the device type.
    private val adSizeDefault:
    // Banner width must match the screen to be fully functional.
            ViewGroup.LayoutParams
        get() {
            // Set the height of the banner ad based on the device type.
            val isTablet = AppLovinSdkUtils.isTablet(activity)
            val heightPx = AppLovinSdkUtils.dpToPx(activity, if (isTablet) 90 else 50)
            // Banner width must match the screen to be fully functional.
            return FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        }
}