package org.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import org.app.core.ads.base.InterAds
import org.app.core.ads.openads.AdapterOpenAppManager

class AdapterInterstitialAds(activity: Activity,
                             adId: String,
                             private val eventId: String,
                             private var tag: String = "AdapterInterstitialAds"
) : InterAds<MaxInterstitialAd?>(activity, adId) {
    
    override fun initAds() {
        super.initAds()
        ads = MaxInterstitialAd(adId, activity)
        initAdListener()
    }

    private fun initAdListener() {
        ads?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d(tag, "-------------------------------------------")
            Log.d(tag, "Revenue: " + ad.revenue)
            Log.d(tag, "NetworkName: " + ad.networkName)
            Log.d(tag, "AdUnitId: " + ad.adUnitId)
            Log.d(tag, "Placement: " + ad.placement)
            Log.d(tag, "-------------------------------------------")
        }

        ads?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(tag, "onAdLoaded $adId")
                onLoadSuccess()
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(tag, "onAdLoadFailed:  $adId -> ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(tag, "onAdDisplayed")
                AdapterOpenAppManager.isAdOtherShowFullScreen = true
                onShowSuccess()
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(tag, "onAdDisplayFailed:  $adId -> ${error.message}")
                onShowError(error.message)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(tag, "onAdHidden")
                AdapterOpenAppManager.isAdOtherShowFullScreen = false
                onClosed()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(tag, "onAdClicked")
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