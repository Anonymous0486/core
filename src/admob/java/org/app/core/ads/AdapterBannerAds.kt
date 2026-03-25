package org.app.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import org.app.core.ads.CoreAds.Companion
import org.app.core.ads.base.BannerAds
import org.app.core.ads.base.CollapsibleType
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.nativeads.AdapterNativeAdView
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.remoteconfig.config.BackupAds
import org.app.core.ads.remoteconfig.type.BackupType
import timber.log.Timber
import java.util.ArrayList
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import androidx.core.view.isEmpty

@SuppressLint("LogNotTimber")
class AdapterBannerAds(
    context: Context,
    container: FrameLayout?,
    adId: String,
    private val eventId: String,
    var adsSize: AdSize? = null,
    var collapsibleType: String? = null,
    var isShowAdsWhenLoaded: Boolean = true,
) : BannerAds<AdView?>(
    context,
    container,
    adId,
    adsSize,
    collapsibleType == CollapsibleType.TOP || collapsibleType == CollapsibleType.BOTTOM
) {
    
    var isFirstDisplay = true
    var isLoaded = false
    var backupAds: LinkedBlockingQueue<BackupAds> = LinkedBlockingQueue<BackupAds>()

    fun isReady() : Boolean {
        return isAvailable && isLoaded
    }

    override fun initAds() {
        super.initAds()
        ads = AdView(context)
        if (adsSize != null) {
            ads?.setAdSize(adsSize!!)
        } else {
            ads?.setAdSize(adSizeDefault)
        }
        ads?.adUnitId = adId
        initAdListener()

        Timber.tag("BannerAdmob").i("$adId init ads that has container: ${container != null}")
        container?.addView(ads)
    }

    private fun initAdListener() {
        ads!!.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()

                logEvent("Loaded")
                Timber.tag("BannerAdmob").i("$adId onAdLoaded that has container: ${container != null} and adView.isCollapsible() is ${ads?.isCollapsible}")

                isLoaded = true
//                try {
//                    if (container != null) {
//                        container?.removeAllViews()
//                        container?.addView(ads)
//                        logEvent("Shown")
//                    }
//                } catch (_: Exception) {}
                onLoadSuccess()
            }

            override fun onAdClosed() {
                super.onAdClosed()
                Timber.tag("BannerAdmob").i("$adId onAdClosed")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Timber.tag("BannerAdmob").i( "$adId onAdFailedToLoad: ${loadAdError.code} - ${loadAdError.message}")
                isLoaded = false

                logEvent("Fail_${loadAdError.code}")
                if (backupAds.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadBackupAds()
                    }, 1500)
                } else {
                    onLoadFailed(loadAdError.message)
                }
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(TAG, "BannerAdmob $adId onAdImpression")
                onShowSuccess()
                logEvent("Impression")

                isFirstDisplay = false
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "BannerAdmob $adId onAdClicked")

                logEvent("Clicked")
            }

            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(TAG, "BannerAdmob $adId onAdOpened")

                if (collapsibleType == CollapsibleType.TOP
                    || collapsibleType == CollapsibleType.BOTTOM
                ) {
                    container?.updateLayoutParams {
                        val h = ads?.adSize?.height ?: 0
                        height = (h * Resources.getSystem().displayMetrics.density).toInt()
                    }
                }
            }
        }
        ads!!.onPaidEventListener = OnPaidEventListener { adValue ->
            Timber.tag("BannerAdmob").i( "$adId onPaidEvent")
            logEvent("Paid")
        }
    }

    override fun loadAds() {
        super.loadAds()

        val rmBackAds = CoreRemoteConfig.instance.getBackupAds(BackupType.BANNER)
            .sortedBy { it.order }
        backupAds.clear()
        if (rmBackAds.isNotEmpty()) {
            backupAds.addAll(rmBackAds)
        }

        val adRequest = AdRequest.Builder()

        if (collapsibleType == CollapsibleType.TOP
            || collapsibleType == CollapsibleType.BOTTOM
        ) {
            val extras = Bundle()
            extras.putString("collapsible", collapsibleType)
            extras.putString("collapsible_request_id", UUID.randomUUID().toString())
            
            adRequest.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }

        logEvent("Request")
        ads!!.loadAd(adRequest.build())
        turnOnAutoReload()
        isFirstDisplay = true
        Timber.tag("BannerAdmob").i( "$adId Start request for loading ads...")
    }

    override fun showAds(activity: Activity) {
        super.showAds(activity)

        try {
            if (container != null) {
                container?.removeAllViews()
                container?.addView(ads)
                logEvent("ShownBackupId")
            } else {
                val cachedContainer = CoreAds.instance.bannerContainer
                if (cachedContainer != null) {
                    cachedContainer.removeAllViews()
                    cachedContainer.addView(ads)
                    logEvent("ShownCached")
                }
            }
        } catch (_: Exception) {}
    }

    override fun destroyAds() {
        super.destroyAds()
        ads?.destroy()
        ads = null
        Log.d(TAG, "BannerAdmob $adId destroyAds")
    }
    
    fun show(container: FrameLayout, callback: AdsCallback?) {
        setAdsCallback(callback)

        if (isLoaded && isFirstDisplay) {
            Timber.tag("BannerAdmob").i( "$adId show with container")
            try {
                if (ads?.parent != null) {
                    (ads?.parent as FrameLayout?)?.removeView(ads)
                }
                container.removeAllViews()
                container.addView(ads)
                logEvent("Shown")
            } catch (e: Exception) {
                Timber.tag("BannerAdmob").i( "Exception: ${e.localizedMessage}")
            }
        } else {
            if (isLoading()) {
                logEvent("Waiting")
                this.container = container
                if (container.isEmpty()) {
                    enableShimmer(adsSize)
                }
            }
        }
    }

    private fun logEvent(type: String) {
        CoreAds.instance.logFirebaseEvent(eventId + "_$type")
    }

    private fun loadBackupAds() {
        val backupId = backupAds.poll()?.id ?: ""

        if (backupId.isBlank()) {
            logEvent("RequestBackupIdButEmpty")
            Log.i(TAG, "BannerAdmob loadBackupAds but empty")
            return
        }

        Log.i(TAG, "BannerAdmob loadBackupAds $backupId")
        ads?.destroy()
        ads = null

        ads = AdView(context)
        if (adsSize != null) {
            ads?.setAdSize(adsSize!!)
        } else {
            ads?.setAdSize(adSizeDefault)
        }
        ads?.adUnitId = backupId

        ads!!.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()

                logEvent("LoadedBackupId")
                Log.d(TAG, "BannerAdmob $backupId onAdLoadedBackupId: ${container != null}")
                onLoadSuccess()

                isLoaded = true
                try {
                    if (container != null) {
                        container?.removeAllViews()
                        container?.addView(ads)
                        logEvent("ShownBackupId")
                    } else {
                        CoreAds.instance.updateTimestamp(System.currentTimeMillis())
                    }
                } catch (_: Exception) {}
            }

            override fun onAdClosed() {
                super.onAdClosed()
                Log.d(TAG, "BannerAdmob $backupId onAdClosed BackupId")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.d(TAG, "BannerAdmob $backupId onAdFailedToLoad: ${loadAdError.code} - ${loadAdError.message}")
                if (backupAds.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadBackupAds()
                    }, 1500)
                } else {
                    isLoaded = false
                    ads?.destroy()
                    ads = null
                    onLoadFailed(loadAdError.message)
                }
                logEvent("FailBackupId_${loadAdError.code}")
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(TAG, "BannerAdmob $backupId onAdImpressionBackupId")
                onShowSuccess()
                logEvent("ImpressionBackupId")

                isFirstDisplay = false
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "BannerAdmob $backupId onAdClickedBackupId")

                logEvent("ClickedBackupId")
            }

            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(TAG, "BannerAdmob $backupId onAdOpenedBackupId")

                if (collapsibleType == CollapsibleType.TOP
                    || collapsibleType == CollapsibleType.BOTTOM
                ) {
                    container?.updateLayoutParams {
                        val h = ads?.adSize?.height ?: 0
                        height = (h * Resources.getSystem().displayMetrics.density).toInt()
                    }
                }
            }
        }
        ads!!.onPaidEventListener = OnPaidEventListener { adValue ->
            Log.d(TAG, "BannerAdmob $backupId onPaidEventBackupId")
            logEvent("PaidBackupId")
        }

        val adRequest = AdRequest.Builder()
        if (collapsibleType == CollapsibleType.TOP
            || collapsibleType == CollapsibleType.BOTTOM
        ) {
            val extras = Bundle()
            extras.putString("collapsible", collapsibleType)
            extras.putString("collapsible_request_id", UUID.randomUUID().toString())

            adRequest.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }

        logEvent("RequestBackupId")
        ads!!.loadAd(adRequest.build())
        turnOnAutoReload()
        isFirstDisplay = true
    }

    // Determine the screen width (less decorations) to use for the ad width.
    private val adSizeDefault: AdSize
        get() {
            val outMetrics: DisplayMetrics = context.resources.displayMetrics
            val widthPixels: Int = outMetrics.widthPixels
            val density: Float = outMetrics.density
            val adWidth = (widthPixels / density).toInt()
            if (adsSize == AdSize.FULL_BANNER) {
                return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, adWidth)
            } else {
                return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
            }
        }
}