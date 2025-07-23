package org.app.core.ads.openads

import android.app.Activity
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import org.app.core.ads.CoreAds
import org.app.core.ads.base.OpenAds

class AdapterOpenAds(activity: Activity, adId: String, private var tag: String = "AppOpenLovin") :
    OpenAds<MaxAppOpenAd?>(activity, adId) {

    override fun initAds() {
        super.initAds()
        ads = MaxAppOpenAd(adId, activity)
        initAdListener()
    }

    private fun initAdListener() {
        ads?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d("AdRevenue", "-------------------------------------------")
            Log.d("AdRevenue", "$tag Revenue: " + ad.revenue)
            Log.d("AdRevenue", "$tag NetworkName: " + ad.networkName)
            Log.d("AdRevenue", "$tag AdUnitId: " + ad.adUnitId)
            Log.d("AdRevenue", "$tag Placement: " + ad.placement)
            Log.d("AdRevenue", "-------------------------------------------")
        }

        ads?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "$tag onAdLoaded")
                onLoadSuccess()
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG, "$tag onAdLoadFailed: ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "$tag onAdDisplayed")
                AdapterOpenAppManager.isAdOtherShowFullScreen = true
                onShowSuccess()
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG, "$tag onAdDisplayFailed: ${error.message}")
                onShowError(error.message)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "$tag onAdHidden")
                AdapterOpenAppManager.isAdOtherShowFullScreen = false
                onClosed()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "$tag onAdClicked")
                CoreAds.instance.logFirebaseEvent("ClickSwitchApp")
            }
        })
    }

    override fun loadAds() {
        super.loadAds()
        ads?.loadAd()
    }

    override fun showAds() {
        super.showAds()
        if (ads?.isReady == true) {
            ads?.showAd()
        } else {
            onShowError("Ads are not ready")
        }
    }

    override fun destroyAds() {
        super.destroyAds()
        ads?.destroy()
        ads = null
    }
}