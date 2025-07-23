package org.app.core.ads.nativeads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.*
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeAds
import org.app.core.ads.openads.AdapterOpenAppManager
import org.app.core.ads.utils.convertToCamelCase

class AdapterNativeAds(
    val context: Context,
    activity: Activity,
    container: FrameLayout?,
    @LayoutRes private var layoutAdId: Int,
    adId: String,
    private val eventId: String,
) : NativeAds<MaxNativeAdView?>(activity, container, adId) {

    private val tag = "NativeAdAdapter"
    
    private var nativeAdLoader: MaxNativeAdLoader? = null

    override fun initAds() {
        super.initAds()
        val layoutAd = LayoutInflater.from(context).inflate(layoutAdId, container, false)

        val binder = MaxNativeAdViewBinder.Builder(layoutAd)
            .setTitleTextViewId(R.id.ad_headline)
            .setBodyTextViewId(R.id.ad_body)
            .setAdvertiserTextViewId(R.id.ad_advertiser)
            .setIconImageViewId(R.id.ad_app_icon)
            .setMediaContentViewGroupId(R.id.ad_media)
            .setOptionsContentViewGroupId(R.id.ad_options_view)
            .setCallToActionButtonId(R.id.ad_call_to_action)
            .build()
        ads = MaxNativeAdView(binder, context)

        nativeAdLoader = MaxNativeAdLoader(adId, context)
        initAdListener()
    }

    private fun initAdListener() {
        nativeAdLoader?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d(tag, "-------------------------------------------")
            Log.d(tag, "NativeMax Revenue: " + ad.revenue)
            Log.d(tag, "NativeMax NetworkName: " + ad.networkName)
            Log.d(tag, "NativeMax AdUnitId: " + ad.adUnitId)
            Log.d(tag, "NativeMax Placement: " + ad.placement)
            Log.d(tag, "-------------------------------------------")
        }
        nativeAdLoader?.setNativeAdListener(object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                if (container != null) {
                    Log.d(tag, "onNativeAdLoaded: $adId and show")
                    onLoadSuccess()
                    
                    container?.visibility = View.VISIBLE
                    container?.removeAllViews()
                    onShowSuccess()
                    populateNativeAdView(ad.nativeAd, nativeAdView)
                    container!!.addView(nativeAdView)
                } else {
                    val adsContainer = CoreAds.instance.adapterNativeAdsViewsStorage[adId]?.container
                    populateNativeAdView(ad.nativeAd, nativeAdView)
                    
                    if (adsContainer != null) {
                        Log.d(tag, "onNativeAdLoaded: $adId -> show ads with cached container")
                        adsContainer.visibility = View.VISIBLE
                        adsContainer.removeAllViews()
                        onShowSuccess()
                        adsContainer.addView(nativeAdView)
                    } else {
                        Log.d(tag, "onNativeAdLoaded: $adId -> save for next show")
                        CoreAds.instance.adapterNativeAdsViewsStorage[adId]?.listNativeAdView?.add(
                            CustomAdapterNativeAdView(isDisplayed = false, adapterNativeAdView = nativeAdView)
                        )
                        onLoadSuccess()
                    }
                    
                    ads = null
                }
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(tag, "onNativeAdLoadFailed $adId: ${error.message}")
                onLoadFailed(error.message)
            }

            override fun onNativeAdClicked(ad: MaxAd) {
                AdapterOpenAppManager.isAdOtherClicked = true
                CoreAds.instance.logFirebaseEvent(eventId)
            }

            override fun onNativeAdExpired(ad: MaxAd) {
                Log.d(tag, "onNativeAdExpired")
            }
        })
    }

    override fun loadAds() {
        super.loadAds()
        Log.d(tag, "Start loading ad: $adId")
        nativeAdLoader?.loadAd(ads)
    }

    override fun destroyAds() {
        super.destroyAds()
        nativeAdLoader?.destroy()
        nativeAdLoader = null
        ads = null
    }

    private fun populateNativeAdView(nativeAd: MaxNativeAd?, adView: MaxNativeAdView?) {
        // The headline is guaranteed to be in every UnifiedNativeAd.
        try {
            adView?.titleTextView?.text = nativeAd?.title ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        try {
            if (nativeAd?.body == null) {
                adView?.bodyTextView?.visibility = View.GONE
            } else {
                adView?.bodyTextView?.visibility = View.VISIBLE
                adView?.bodyTextView?.text = nativeAd.body
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.advertiser == null) {
                adView?.advertiserTextView?.visibility = View.GONE
            } else {
                adView?.advertiserTextView?.visibility = View.VISIBLE
                adView?.advertiserTextView?.text = nativeAd.advertiser
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.icon == null) {
                adView?.iconImageView?.visibility = View.GONE
            } else {
                adView?.iconImageView?.visibility = View.VISIBLE
                adView?.iconImageView?.setImageURI(nativeAd.icon!!.uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.mediaView == null) {
                adView?.mediaContentViewGroup?.visibility = View.GONE
            } else {
                adView?.mediaContentViewGroup?.visibility = View.VISIBLE
                adView?.mediaContentViewGroup?.removeAllViews()
                adView?.mediaContentViewGroup?.addView(nativeAd.mediaView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.optionsView == null) {
                adView?.optionsContentViewGroup?.visibility = View.GONE
            } else {
                adView?.optionsContentViewGroup?.visibility = View.VISIBLE
                adView?.optionsContentViewGroup?.removeAllViews()
                adView?.optionsContentViewGroup?.addView(nativeAd.optionsView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd?.callToAction == null) {
                adView?.callToActionButton?.visibility = View.GONE
            } else {
                adView?.callToActionButton?.visibility = View.VISIBLE
                adView?.callToActionButton?.text = nativeAd.callToAction!!.split(",")
                    .toTypedArray()[0].convertToCamelCase()
                val bg = CoreAds.instance.ctaBackgroundDrawable
                bg?.let {
                    adView?.callToActionButton?.background = AppCompatResources.getDrawable(context, bg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}