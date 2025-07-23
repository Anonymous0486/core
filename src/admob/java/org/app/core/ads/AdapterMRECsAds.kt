package org.app.core.ads

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import org.app.core.ads.base.BannerAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.openads.AdapterOpenAppManager

class AdapterMRECsAds(
    activity: Activity,
    container: FrameLayout,
    adId: String,
    private val eventId: String,
    var isShowAdsWhenLoaded: Boolean = true
) : BannerAds<AdView?>(activity, container, adId) {
    
    override fun initAds() {
        super.initAds()
        ads = AdView(activity)
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
                
                if (isShowAdsWhenLoaded && CoreAds.isNetworkAvailable(activity)) {
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
                
                if (!CoreAds.isNetworkAvailable(activity)) {
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
            Log.d(TAG, "BannerAdmob onPaidEvent")
        }
    }
    
    override fun loadAds() {
        super.loadAds()
        
        val adRequest = AdRequest.Builder()
        
        ads!!.loadAd(adRequest.build())
        turnOffAutoReload()
    }
    
    override fun showAds() {
        super.showAds()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = activity.windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                
                val adWidthPixels = bounds.width().toFloat()
                
                val density = activity.resources.displayMetrics.density
                val adWidth = (adWidthPixels / density).toInt()
                val delta = 16 * density.toInt()
                
                return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth - delta)
            } else {
                val display = activity.windowManager.defaultDisplay
                val outMetrics = DisplayMetrics()
                display.getMetrics(outMetrics)
                val widthPixels = outMetrics.widthPixels.toFloat()
                val density = outMetrics.density
                val adWidth = (widthPixels / density).toInt()
                val delta = 16 * density.toInt()
                
                return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth - delta)
            }
        }
}