package org.app.core.ads.nativeads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeAds
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.remoteconfig.config.BackupAds
import org.app.core.ads.remoteconfig.type.BackupType
import org.app.core.ads.utils.convertToCamelCase
import java.util.concurrent.LinkedBlockingQueue

@SuppressLint("LogNotTimber")
class AdapterNativeAds(
    val context: Context,
    activity: Activity,
    container: FrameLayout?,
    @LayoutRes private var layoutAdId: Int,
    adId: String,
    private val firebaseEventId: String,
    private val isMultiple: Boolean = false,
) : NativeAds<NativeAdView?>(activity, container, adId) {
    
    private var nativeAdLoader: AdLoader? = null
    private var retry: Int = 0
    private var alreadyShown: Boolean = false
    var backupAds: LinkedBlockingQueue<BackupAds> = LinkedBlockingQueue<BackupAds>()

    override fun initAds() {
        super.initAds()
        retry = 0
        alreadyShown = false
        setupOptionLoadRequest()
    }

    override fun loadAds() {
        super.loadAds()
        logEvent("Request")

        backupAds.clear()
        val rmBackAds = CoreRemoteConfig.instance.getBackupAds(BackupType.NATIVE)
            .sortedBy { it.order }
        if (rmBackAds.isNotEmpty()) {
            backupAds.addAll(rmBackAds)
        }
        Log.d(TAG, "NativeAdmob Start request ads")
        nativeAdLoader?.loadAd(AdRequest.Builder().build())
    }

    override fun destroyAds() {
        super.destroyAds()
        ads?.destroy()
        ads = null
        nativeAdLoader = null
        
        Log.d(TAG, "NativeAdmob destroyAds")
    }

    private fun setupOptionLoadRequest() {
        // Need Check more about option is needed?
//        val videoOptions =
//            VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true).build()
//        val nativeAdOptions: NativeAdOptions = Builder()
//            .setMediaAspectRatio(MediaAspectRatio.ANY)
//            .setVideoOptions(videoOptions)
//            .build()
        
        val builder = AdLoader.Builder(context, adId).forNativeAd { nativeAd: NativeAd ->
            val layoutAd = LayoutInflater.from(context).inflate(layoutAdId, container, false)
            
            ads = NativeAdView(context)
            ads?.addView(layoutAd)
            
            nativeAd.setOnPaidEventListener { adValue ->
                Log.i(TAG, "NativeAdmob onPaidEvent $adId")
                logEvent("Paid")
            }
            
            Log.i(TAG, "NativeAdmob: In init should show: ${container != null} -> $alreadyShown")
            try {
                val aAdView = AdapterNativeAdView(false, nativeAd, null)
                if (container != null && !alreadyShown) {
                    alreadyShown = true
                    container!!.visibility = View.VISIBLE
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = aAdView
                    populateNativeAdView(nativeAd, ads)
                    container!!.removeAllViews()
                    container!!.addView(ads)
                    logEvent("Shown")
                } else {
                    val cachedContainer = CoreAds.instance.nativeContainer

                    if (cachedContainer != null  && !alreadyShown) {
                        Log.d(TAG, "NativeAdmob multiple-load $adId -> show with cached container")
                        alreadyShown = true
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null

                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = aAdView
                        populateNativeAdView(nativeAd, ads)
                        cachedContainer.visibility = View.VISIBLE
                        cachedContainer.removeAllViews()
                        cachedContainer.addView(ads)

                        logEvent("Shown")
                    } else {
                        Log.d(TAG, "NativeAdmob multiple-load $adId -> save for next show")
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.nativeAds?.add(aAdView)
                    }
                }
            } catch (_: Exception) {}
        }.forAdManagerAdView({ adView ->
            try {
                if (container != null && !alreadyShown) {
                    alreadyShown = true
                    container!!.visibility = View.VISIBLE
                    container!!.removeAllViews()
                    container!!.addView(adView)
                    logEvent("ShownMixBanner")
                } else {
                    val cachedContainer = CoreAds.instance.nativeContainer

                    if (cachedContainer != null  && !alreadyShown) {
                        Log.d(TAG, "NativeAdmob MixBanner $adId -> show with cached container")
                        alreadyShown = true
                        cachedContainer.visibility = View.VISIBLE
                        cachedContainer.removeAllViews()
                        cachedContainer.addView(adView)

                        logEvent("ShownMixBanner")
                    } else {
                        logEvent("ShownMixBannerMissed")
                        alreadyShown = false
                        val aAdView = AdapterNativeAdView(false, null, null, adView)
                        Log.d(TAG, "NativeAdmob MixBanner $adId -> save for next show")
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.nativeAds?.add(aAdView)
                    }
                }
            } catch (_: Exception) {}

        }, AdSize.BANNER, AdSize.FULL_BANNER)

        nativeAdLoader = builder
            .withAdListener(object : AdListener() {
                override fun onAdOpened() {
                    super.onAdOpened()
                    Log.d(TAG, "NativeAdmob onAdOpened")
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    Log.d(TAG, "NativeAdmob onAdFailedToLoad: ${loadAdError.code} -> ${loadAdError.message} -> $adId")

                    if (backupAds.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    } else {
                        onLoadFailed(loadAdError.message)
                    }

                    logEvent("LoadFail_${loadAdError.code}")
                }
                
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    val cSize = CoreAds.instance.admobNativeAdsViewsStorage[adId]?.nativeAds?.size ?: 0
                    Log.d(TAG, "NativeAdmob onAdLoaded -> $adId - $cSize")
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.isLoading = false
                    onLoadSuccess()
                    logEvent("Loaded")
                }
                
                override fun onAdClosed() {
                    super.onAdClosed()
                    Log.d(TAG, "NativeAdmob onAdClosed")
                }
                
                override fun onAdImpression() {
                    super.onAdImpression()
                    Log.i(TAG, "NativeAdmob onAdImpression $adId")
                    onShowSuccess()
                    
                    logEvent("Impression")
                }
                
                override fun onAdClicked() {
                    super.onAdClicked()
                    Log.d(TAG, "NativeAdmob onAdClicked")
                    logEvent("Clicked")
                }
            })
//            .withNativeAdOptions(nativeAdOptions)
            .build()
    }

    private fun loadBackupAds() {
        val backupId = backupAds.poll()?.id ?: ""

        if (backupId.isBlank()) {
            Log.i(TAG, "NativeAdmob loadBackupAds but empty")
            logEvent("RequestBackupIdButEmpty")
            return
        }

        ads?.destroy()
        ads = null
        nativeAdLoader = null

        Log.i(TAG, "NativeAdmob: loadBackupAds: ${container == null} -> $backupId")
        val builder = AdLoader.Builder(context, backupId).forNativeAd { nativeAd: NativeAd ->
            val layoutAd = LayoutInflater.from(context).inflate(layoutAdId, container, false)

            ads = NativeAdView(context)
            ads?.addView(layoutAd)

            nativeAd.setOnPaidEventListener { adValue ->
                Log.i(TAG, "NativeAdmob onPaidEvent $backupId")
                logEvent("PaidBackupId")
            }

            Log.i(TAG, "NativeAdmob: multiple-load in init should not show Native ${container == null} -> $backupId")
            try {
                CoreAds.instance.admobNativeAdsViewsStorage[adId]?.isLoading = false
                val aAdView = AdapterNativeAdView(false, nativeAd, null)
                if (container != null && !alreadyShown) {
                    alreadyShown = true
                    container!!.visibility = View.VISIBLE
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = aAdView
                    populateNativeAdView(nativeAd, ads)
                    container!!.removeAllViews()
                    container!!.addView(ads)

                    logEvent("ShownBackupId")
                } else {
                    val cachedContainer = CoreAds.instance.nativeContainer

                    if (cachedContainer != null && !alreadyShown) {
                        alreadyShown = true
                        Log.d(TAG, "NativeAdmob multiple-load $backupId -> show with cached container")
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.lastDisplayAds = aAdView
                        populateNativeAdView(nativeAd, ads)
                        cachedContainer.visibility = View.VISIBLE
                        cachedContainer.removeAllViews()
                        cachedContainer.addView(ads)

                        logEvent("ShownBackupId")
                    } else {
                        Log.d(TAG, "NativeAdmob multiple-load $backupId -> save for next show")
                        CoreAds.instance.admobNativeAdsViewsStorage[adId]?.nativeAds?.add(aAdView)
                    }
                }

            } catch (_: Exception) {}
        }

        nativeAdLoader = builder
            .withAdListener(object : AdListener() {
                override fun onAdOpened() {
                    super.onAdOpened()
                    Log.d(TAG, "NativeAdmob onAdOpened")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    Log.d(TAG, "NativeAdmob onAdFailedToLoad: ${loadAdError.code} -> ${loadAdError.message} -> $backupId")

                    logEvent("LoadFailBackupId_${loadAdError.code}")
                    if (backupAds.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    } else {
                        onLoadFailed(loadAdError.message)
                    }
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    val cSize = CoreAds.instance.admobNativeAdsViewsStorage[adId]?.nativeAds?.size ?: 0
                    Log.d(TAG, "NativeAdmob onAdLoaded -> $backupId - $cSize")
                    onLoadSuccess()
                    logEvent("LoadedBackupId")
                    CoreAds.instance.admobNativeAdsViewsStorage[adId]?.isLoading = false
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    Log.d(TAG, "NativeAdmob onAdClosed")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    Log.i(TAG, "NativeAdmob onAdImpression $adId")
                    onShowSuccess()

                    logEvent("ImpressionBackupId")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    Log.d(TAG, "NativeAdmob onAdClicked")
                    logEvent("ClickedBackupId")
                }
            })
            .build()

        logEvent("RequestBackupId")
        nativeAdLoader?.loadAd(AdRequest.Builder().build())
    }
    
    private fun populateNativeAdView(nativeAd: NativeAd?, adView: NativeAdView?) {
        val mediaView = adView?.findViewById<MediaView>(R.id.ad_media)
        mediaView?.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        adView?.mediaView = mediaView

        // Set other ad assets.
        adView?.headlineView = adView?.findViewById(R.id.ad_headline)
        adView?.bodyView = adView?.findViewById(R.id.ad_body)
        adView?.callToActionView = adView?.findViewById(R.id.ad_call_to_action)
        adView?.iconView = adView?.findViewById(R.id.ad_app_icon)
        adView?.storeView = adView?.findViewById(R.id.ad_advertiser)
        adView?.apply {
            starRatingView = findViewById(resources.getIdentifier("ad_rating", "id", context.packageName))
        }

        // The headline is guaranteed to be in every UnifiedNativeAd.
        try {
            (adView?.headlineView as TextView?)?.text = nativeAd?.headline
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        try {
            if (nativeAd?.body == null) {
                adView?.bodyView?.visibility = View.GONE
            } else {
                adView?.bodyView?.visibility = View.VISIBLE
                (adView?.bodyView as? TextView)?.text = nativeAd.body
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.callToAction == null) {
                adView?.callToActionView?.visibility = View.GONE
            } else {
                adView?.callToActionView?.visibility = View.VISIBLE
                (adView?.callToActionView as? Button)?.text = nativeAd.callToAction!!.split(",")
                    .toTypedArray()[0].convertToCamelCase()
                val bg = CoreAds.instance.ctaBackgroundDrawable
                bg?.let {
                    adView?.callToActionView?.background = AppCompatResources.getDrawable(context, bg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.icon == null) {
                adView?.iconView?.visibility = View.GONE
            } else {
                (adView?.iconView as ImageView?)?.setImageDrawable(
                    nativeAd.icon?.drawable
                )
                adView?.iconView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.price == null) {
                adView?.priceView?.visibility = View.GONE
            } else {
                adView?.priceView?.visibility = View.VISIBLE
                (adView?.priceView as? TextView)?.text = nativeAd.price
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.store == null) {
                adView?.storeView?.visibility = View.GONE
            } else {
                adView?.storeView?.visibility = View.VISIBLE
                (adView?.storeView as? TextView)?.text = nativeAd.store
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.starRating == null) {
                adView?.starRatingView?.visibility = View.GONE
            } else {
                (adView?.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
                adView?.starRatingView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.advertiser == null) {
                adView?.advertiserView?.visibility = View.GONE
            } else {
                (adView?.advertiserView as? TextView)?.text = nativeAd.advertiser
                adView?.advertiserView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad. The SDK will populate the adView's MediaView
        // with the media content from this native ad.
        if (nativeAd != null) adView?.setNativeAd(nativeAd)
    }
    
    private fun logEvent(type: String) {
        val param = Bundle().apply {
            putString("event_type", type)
        }
        CoreAds.instance.logFirebaseEvent(firebaseEventId, param)
    }
}