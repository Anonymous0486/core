package org.app.core.ads

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.appevents.AppEventsConstants.EVENT_NAME_AD_IMPRESSION
import com.facebook.appevents.AppEventsConstants.EVENT_PARAM_CURRENCY
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import org.app.core.ads.base.BannerAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.openads.AdapterOpenAppManager
import timber.log.Timber

class AdapterMRECsAds(
    context: Context,
    container: FrameLayout,
    adId: String,
    private val eventId: String,
    var isShowAdsWhenLoaded: Boolean = true
) : BannerAds<AdView?>(context, container, adId) {
    
    override fun initAds() {
        super.initAds()
        ads = AdView(context)
        ads?.setAdSize(adSizeDefault)
        ads?.adUnitId = adId
        initAdListener()
    }
    
    private fun initAdListener() {
        ads!!.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "BannerAdmob onAdLoaded")
                onLoadSuccess()
                
                if (isShowAdsWhenLoaded && CoreAds.isNetworkAvailable(context)) {
                    container?.visibility = View.VISIBLE
                }
            }
            
            override fun onAdClosed() {
                super.onAdClosed()
                Log.d(TAG, "BannerAdmob onAdClosed")
            }
            
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.d(TAG, "BannerAdmob onAdFailedToLoad: ${loadAdError.message}")
                onLoadFailed(loadAdError.message)
                
                if (!CoreAds.isNetworkAvailable(context)) {
                    container?.visibility = View.GONE
                }
            }
            
            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(TAG, "BannerAdmob onAdImpression")
                onShowSuccess()
            }
            
            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "BannerAdmob onAdClicked")
                
                val param = Bundle().apply {
                    this.putString("eventType", "Clicked")
                }
                CoreAds.instance.logFirebaseEvent(eventId, param)
            }
            
            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(TAG, "BannerAdmob onAdOpened")
            }
        }
        ads!!.onPaidEventListener = OnPaidEventListener { adValue ->
            val value = (adValue.valueMicros / 1000000.0)
            Timber.tag(TAG).i("AdapterMRECsAds onPaidEvent -> ${adValue.valueMicros} - $value - ${adValue.currencyCode}")

            val params = Bundle()
            params.putString(EVENT_PARAM_CURRENCY, "USD")
            CoreAds.instance.logFbEvent(EVENT_NAME_AD_IMPRESSION, value, params)
        }
    }
    
    override fun loadAds() {
        super.loadAds()
        
        val adRequest = AdRequest.Builder()
        
        ads!!.loadAd(adRequest.build())
        turnOffAutoReload()
    }
    
    override fun showAds(activity: Activity) {
        super.showAds(activity)
        container?.addView(ads)
    }
    
    override fun destroyAds() {
        super.destroyAds()
        ads?.destroy()
        ads = null
    }
    
    fun show(container: FrameLayout, callback: AdsCallback?) {
        if (ads?.parent != null) {
            (ads?.parent as FrameLayout?)?.removeView(ads)
        }
        
        if (this.container != null) {
            this.container?.removeAllViews()
        }
        
        setAdsCallback(callback)
        this.container = container
        container.removeAllViews()
        container.addView(ads)
    }
    
    // Determine the screen width (less decorations) to use for the ad width.
    private val adSizeDefault: AdSize
        get() {
            val outMetrics: DisplayMetrics = context.resources.displayMetrics
            val widthPixels: Int = outMetrics.widthPixels
            val density: Float = outMetrics.density
            val adWidth = (widthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
        }
}