package org.app.core.ads.openads

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import org.app.core.ads.CoreAds
import org.app.core.ads.base.OpenAds

class AdapterOpenAds(activity: Activity,
                     adId: String,
                     private val eventId: String,
                     private var tag: String = "AppOpenAdmob") :
    OpenAds<AppOpenAd?>(activity, adId) {

    override fun loadAds() {
        super.loadAds()
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            activity,
            adId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "$tag onAdLoaded. Adapter class name: ${ad.responseInfo.mediationAdapterClassName}")
                    ad.fullScreenContentCallback = mListener
                    ad.onPaidEventListener = onPaidEventListener
                    ads = ad
                    onLoadSuccess()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d(TAG, "$tag onAdFailedToLoad: ${loadAdError.code}")
                    Log.d(TAG, "$tag onAdFailedToLoad: ${loadAdError.message}")
                    Log.d(TAG, "$tag onAdFailedToLoad: ${loadAdError.responseInfo}")
                    onLoadFailed(loadAdError.message)
                }
            })
    }

    override fun showAds() {
        super.showAds()
        ads?.show(activity)
    }

    override fun destroyAds() {
        super.destroyAds()
        ads = null
    }

    private var mListener: AdmobOpenAdsCallback? = null
        get() {
            if (field == null) {
                field = AdmobOpenAdsCallback()
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

    private inner class AdmobOpenAdsCallback : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.d(TAG, "$tag onAdFailedToShowFullScreenContent: ${adError.message}")
            onShowError(adError.message)
        }

        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "$tag onAdDismissedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = false
            onClosed()
        }

        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "$tag onAdShowedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = true
            onShowSuccess()
        }

        override fun onAdClicked() {
            Log.d(TAG, "$tag onAdClicked")
            val param = Bundle().apply {
                this.putString("eventType", "Clicked")
            }
    
            CoreAds.instance.logFirebaseEvent(eventId, param)
        }

        override fun onAdImpression() {
            Log.d(TAG, "$tag onAdImpression")
        }
    }

    private inner class AdmobPaidEventCallback : OnPaidEventListener {
        override fun onPaidEvent(adValue: AdValue) {
            Log.d(TAG, "$tag onPaidEvent")
        }
    }
}