package org.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.app.core.ads.openads.AdapterOpenAppManager
import org.app.core.ads.base.RewardAds

class AdapterRewardAds(activity: Activity, adId: String, private val eventId: String) :
    RewardAds<RewardedAd?>(activity, adId) {

    override fun loadAds() {
        super.loadAds()
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            adId,
            adRequest,
            object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "RewardAdmob onAdLoaded. Adapter class name: ${rewardedAd.responseInfo.mediationAdapterClassName}")
                rewardedAd.fullScreenContentCallback = mListener
                rewardedAd.onPaidEventListener = onPaidEventListener
                ads = rewardedAd
                onLoadSuccess()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d(TAG, "RewardAdmob onAdLoadFailed: ${loadAdError.message}")
                onLoadFailed(loadAdError.message)
            }
        })
    }

    override fun showAds() {
        ads?.show(activity){
            Log.d(TAG, "RewardAdmob onUserRewarded: amount=${it.amount}, type=${it.type}")
            onUserRewarded(it.amount, it.type)
        }
    }

    override fun destroyAds() {
        super.destroyAds()
        ads = null
    }

    private var mListener: AdmobRewardCallback? = null
        get() {
            if (field == null) {
                field = AdmobRewardCallback()
            }
            return field
        }

    private var onPaidEventListener: AdmobPaidEventCallback? = null
        get() {
            if (field == null) {
                field = AdmobPaidEventCallback()
            }
            return field
        }

    private inner class AdmobRewardCallback : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.d(TAG, "RewardAdmob onAdFailedToShowFullScreenContent: ${adError.message}")
            onShowError(adError.message)
        }

        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "RewardAdmob onAdDismissedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = false
            onClosed()
        }

        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "RewardAdmob onAdShowedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = true
            onShowSuccess()
        }

        override fun onAdClicked() {
            Log.d(TAG, "RewardAdmob onAdClicked")
    
            val param = Bundle().apply {
                this.putString("eventType", "Clicked")
            }
            CoreAds.instance.logFirebaseEvent(eventId, param)
        }

        override fun onAdImpression() {
            Log.d(TAG, "RewardAdmob onAdImpression")
        }
    }

    private inner class AdmobPaidEventCallback : OnPaidEventListener {
        override fun onPaidEvent(adValue: AdValue) {
            Log.d(TAG, "RewardAdmob onPaidEvent")
        }
    }
}