package org.app.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.*
import org.app.core.ads.base.InterAds
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.app.core.ads.dialog.DialogAdsLoading
import org.app.core.ads.openads.AdapterOpenAppManager
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.remoteconfig.config.BackupAds
import org.app.core.ads.remoteconfig.type.BackupType
import java.util.concurrent.LinkedBlockingQueue

@SuppressLint("LogNotTimber")
class AdapterInterstitialAds(activity: Activity,
                             adId: String,
                             private val eventId: String,
                             private var tag: String = "InterstitialAdmob") :
    InterAds<InterstitialAd?>(activity, adId) {

    var backupAds: LinkedBlockingQueue<BackupAds> = LinkedBlockingQueue<BackupAds>()
    var isBackupId = false

    override fun loadAds() {
        super.loadAds()
        val adRequest = AdRequest.Builder().build()
        logEvent("Request")
        isBackupId = false
        val rmBackAds = CoreRemoteConfig.instance.getBackupAds(BackupType.INTERSTITIAL)
            .sortedBy { it.order }
        backupAds.clear()
        if (rmBackAds.isNotEmpty()) {
            backupAds.addAll(rmBackAds)
        }
        Log.i(TAG, "$tag start load inter. $adId - ${backupAds.size}")

        InterstitialAd.load(
            activity,
            adId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.i(TAG, "$tag onAdLoaded. $adId")
                    interstitialAd.fullScreenContentCallback = mListener
                    interstitialAd.onPaidEventListener = onPaidEventListener
                    ads = interstitialAd
                    onLoadSuccess()
                    logEvent("Loaded")
                    if (showWhenLoaded) {
                        logEvent("Shown")
                        ads?.show(activity)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.i(TAG, "$tag onAdFailedToLoad: ${loadAdError.code} - ${loadAdError.message}")

                    if (backupAds.isEmpty()) {
                        onLoadFailed(loadAdError.message)
                    } else {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    }
                    logEvent("Fail_${loadAdError.code}")
                }
            })
    }

    override fun showAds() {
        super.showAds()

        logEvent(if (isBackupId) "ShownBackupId" else "Shown")
        ads?.show(activity)
    }

    override fun destroyAds() {
        super.destroyAds()
        ads = null
        Log.i(TAG, "$tag Destroy inter $adId")
    }

    private fun loadBackupAds() {
        val backupId = backupAds.poll()?.id ?: ""

        if (backupId.isBlank()) {
            Log.i(TAG, "$tag loadBackupAds but empty")
            logEvent("RequestBackupIdButEmpty")
            onLoadFailed("Failed")
            return
        }

        Log.i(TAG, "$tag loadBackupAds:$backupId")
        ads = null
        val adRequest = AdRequest.Builder().build()
        logEvent("RequestBackupId")
        isBackupId = true
        InterstitialAd.load(
            activity,
            backupId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.i(TAG, "$tag onAdLoadedBackupId. $backupId")
                    interstitialAd.fullScreenContentCallback = mListener
                    interstitialAd.onPaidEventListener = onPaidEventListener
                    ads = interstitialAd
                    onLoadSuccess()
                    logEvent("LoadedBackupId")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.i(TAG, "$tag onAdFailedToLoadBackupId: ${loadAdError.code} - ${loadAdError.message} $backupId")
                    logEvent("FailBackupId_${loadAdError.code}")
                    if (backupAds.isEmpty()) {
                        onLoadFailed(loadAdError.message)
                    } else {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    }
                }
            })
    }

    private var mListener: AdmobInterstitialCallback? = null
        get() {
            if (field == null) {
                field = AdmobInterstitialCallback()
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

    private inner class AdmobInterstitialCallback : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.i(TAG, "$tag onAdFailedToShowFullScreenContent: ${adError.message}")
            onShowError(adError.message)
            logEvent(if (isBackupId) "DisplayFailBackupId" else "DisplayFail")
        }

        override fun onAdDismissedFullScreenContent() {
            Log.i(TAG, "$tag onAdDismissedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = false
            onClosed()
        }

        override fun onAdShowedFullScreenContent() {
            Log.i(TAG, "$tag onAdShowedFullScreenContent")
            AdapterOpenAppManager.isAdOtherShowFullScreen = true
            onShowSuccess()
            logEvent(if (isBackupId) "DisplaySuccessBackupId" else "DisplaySuccess")
        }

        override fun onAdClicked() {
            Log.i(TAG, "$tag onAdClicked")

            logEvent("Click")
        }

        override fun onAdImpression() {
            Log.i(TAG, "$tag onAdImpression")
            logEvent(if (isBackupId) "ImpressionBackupId" else "Impression")
        }
    }

    private fun logEvent(type: String) {
        CoreAds.instance.logFirebaseEvent(eventId + "_$type")
    }

    private inner class AdmobPaidEventCallback : OnPaidEventListener{
        override fun onPaidEvent(adValue: AdValue) {
            Log.i(TAG, "$tag onPaidEvent")
            logEvent("Paid")
        }
    }
}