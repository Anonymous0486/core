package org.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.applovin.mediation.*
import com.applovin.mediation.ads.MaxRewardedAd
import org.app.core.ads.base.RewardAds
import org.app.core.ads.openads.AdapterOpenAppManager

class AdapterRewardAds(activity: Activity,
                       adId: String,
                       private val eventId: String
) : RewardAds<MaxRewardedAd?>(activity, adId) {

    override fun initAds() {
        super.initAds()
        ads = MaxRewardedAd.getInstance(adId, activity)
        initAdListener()
    }

    private fun initAdListener() {
        ads?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d("AdRevenue", "-------------------------------------------")
            Log.d("AdRevenue", "RewardMax Revenue: " + ad.revenue)
            Log.d("AdRevenue", "RewardMax NetworkName: " + ad.networkName)
            Log.d("AdRevenue", "RewardMax AdUnitId: " + ad.adUnitId)
            Log.d("AdRevenue", "RewardMax Placement: " + ad.placement)
            Log.d("AdRevenue", "-------------------------------------------")
        }

        ads?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "RewardMax onAdLoaded")
                onLoadSuccess()
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG, "RewardMax onAdLoadFailed: ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "RewardMax onAdDisplayed")
                AdapterOpenAppManager.isAdOtherShowFullScreen = true
                onShowSuccess()
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG, "RewardMax onAdDisplayFailed: ${error.message}")
                onShowError(error.message)
            }

            override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                Log.d(
                    TAG,
                    "RewardMax onUserRewarded: amount=${reward.amount}, label=${reward.label}"
                )
                onUserRewarded(reward.amount, reward.label)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "RewardMax onAdHidden")
                AdapterOpenAppManager.isAdOtherShowFullScreen = false
                onClosed()
            }

            override fun onRewardedVideoStarted(ad: MaxAd) {
                Log.d(TAG, "RewardMax onRewardedVideoStarted")
            }

            override fun onRewardedVideoCompleted(ad: MaxAd) {
                Log.d(TAG, "RewardMax onRewardedVideoCompleted")
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "RewardMax onAdClicked")
                val param = Bundle().apply {
                    this.putString("eventType", "Clicked")
                }
                CoreAds.instance.logFirebaseEvent(eventId, param)
            }
        })
    }

    override fun loadAds() {
        ads?.loadAd()
    }

    override fun showAds() {
        if (ads?.isReady == true) {
            ads?.showAd()
        } else {
            onShowError("Ads are not ready")
        }
    }

    override fun destroyAds() {
        ads?.destroy()
        ads = null
    }
}