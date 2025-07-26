package org.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import android.view.ViewGroup
import com.applovin.mediation.MaxAdFormat
import com.applovin.sdk.AppLovinSdkUtils
import org.app.core.ads.base.MRECsAds
import org.app.core.ads.openads.AdapterOpenAppManager

class AdapterMRECsAds(
    activity: Activity,
    container: FrameLayout,
    adId: String,
    private val eventId: String,
    var isShowAdsWhenLoaded: Boolean = true
) : MRECsAds<MaxAdView?>(activity, container, adId) {

    override fun initAds() {
        super.initAds()
        ads = MaxAdView(adId, MaxAdFormat.MREC, activity)
        ads?.layoutParams = adSizeDefault
        initAdListener()
    }

    private fun initAdListener() {
        ads?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d("AdRevenue", "-------------------------------------------")
            Log.d("AdRevenue", "MRECsMax Revenue: " + ad.revenue)
            Log.d("AdRevenue", "MRECsMax NetworkName: " + ad.networkName)
            Log.d("AdRevenue", "MRECsMax AdUnitId: " + ad.adUnitId)
            Log.d("AdRevenue", "MRECsMax Placement: " + ad.placement)
            Log.d("AdRevenue", "-------------------------------------------")
        }

        ads?.setListener(object : MaxAdViewAdListener {
            override fun onAdExpanded(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdExpanded")
            }

            override fun onAdCollapsed(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdCollapsed")
            }

            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdLoaded")
                onLoadSuccess()

                if (isShowAdsWhenLoaded && CoreAds.isNetworkAvailable(activity)) {
                    container?.visibility = View.VISIBLE
                }
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG, "MRECsMax onAdLoadFailed: ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdDisplayed")
                onShowSuccess()

                if (shimmer != null && !activity.isDestroyed) {
                    container?.removeView(shimmer)
                    shimmer = null
                }
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG, "MRECsMax onAdDisplayFailed: ${error.message}")
                onShowError(error.message)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdHidden")
                onClosed()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "MRECsMax onAdClicked")
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
        turnOffAutoReload()
    }

    override fun showAds() {
        super.showAds()
        container?.addView(ads)
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

    // MREC width and height are 300 and 250 respectively, on phones and tablets
    private val adSizeDefault:
            ViewGroup.LayoutParams
        get() {
            val widthPx = AppLovinSdkUtils.dpToPx(activity, 300)
            val heightPx = AppLovinSdkUtils.dpToPx(activity, 250)
            val layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
            layoutParams.gravity = Gravity.CENTER
            return layoutParams
        }
}