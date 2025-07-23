package org.app.core.ads

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.applovin.mediation.MaxAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.app.core.ads.nativeads.AdapterNativeAds
import org.app.core.ads.nativeads.CustomAdapterNativeAdViews
import org.app.core.R
import org.app.core.ads.base.BaseAds
import org.app.core.ads.base.InterAds
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.base.RewardAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.dialog.DialogAdsLoading
import org.app.core.ads.openads.AdapterOpenAds
import org.app.core.ads.utils.showMessage
import org.app.core.feature.CoreFeature
import java.io.File
import java.util.Arrays
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class CoreAds private constructor() {
    private var analytics: FirebaseAnalytics = Firebase.analytics
    private var _enableDebug = false
    @DrawableRes
    private var _ctaBgRes : Int? = null
    
    private var _userConsent = true
    var userConsent: Boolean
        get() = _userConsent
        set(value) { _userConsent = value }
    
    private var _isHideAds = false
    val isHideAds: Boolean
        get() = _isHideAds || !_userConsent
    
    val ctaBackgroundDrawable: Int?
        get() = _ctaBgRes
    
    fun toggleDebug(mode: Boolean? = null) : Boolean {
        _enableDebug = mode ?: !_enableDebug
        
        return _enableDebug
    }
    
    // TODO: For development phase only
    fun setHideAds(flag: Boolean) {
        _isHideAds = flag
    }
    
    fun showAdapterInterstitialSplashAds(
        loadingTxt: String,
        activity: Activity,
        adsId: String,
        eventId: String,
        timeout: Int,
        callback: AdsCallback?
    ) {
        
        if (_isHideAds) return
        
        var splashAds: AdapterInterstitialAds? = AdapterInterstitialAds(activity = activity, adId = adsId, eventId = eventId)
        var splashDone = false

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (splashDone) return@Runnable
            splashDone = true

            splashAds?.destroyAds()
            splashAds = null

            if (activity.isDestroyed || activity.isFinishing) return@Runnable
            callback?.onError("Ad request timed out")
        }
        handler.postDelayed(runnable, timeout.toLong())
        splashAds?.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                splashAds?.turnOffAutoReload()
                if (splashDone) return
                splashDone = true

                handler.removeCallbacksAndMessages(null)
                if (activity.isDestroyed || activity.isFinishing || splashAds == null) {
                    splashAds?.destroyAds()
                    splashAds = null
                    return
                }

                showAdsWithDialogLoading(activity, splashAds!!, callback, loadingTxt)
                splashAds = null
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                splashAds?.load()
            }
        })?.load()
    }

    fun showAdapterOpenSplashAds(
        activity: Activity,
        adsId: String,
        eventId: String,
        timeout: Int,
        callback: AdsCallback?
    ) {
    
        if (_isHideAds) return
        var splashAds: AdapterOpenAds? =
            AdapterOpenAds(activity = activity, adId = adsId)
        var splashDone = false

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (splashDone) return@Runnable
            splashDone = true

            splashAds?.destroyAds()
            splashAds = null

            if (activity.isDestroyed || activity.isFinishing) return@Runnable
            callback?.onError("Ad request timed out")
        }
        handler.postDelayed(runnable, timeout.toLong())
        splashAds?.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                splashAds?.turnOffAutoReload()
                if (splashDone) return
                splashDone = true

                handler.removeCallbacksAndMessages(null)
                if (activity.isDestroyed || activity.isFinishing || splashAds == null) {
                    splashAds?.destroyAds()
                    splashAds = null
                    return
                }

                showAdsWithDialogLoading(activity, splashAds!!, callback)
                splashAds = null
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                splashAds?.load()
            }
        })?.load()
    }

    // ----------------------- Interstitial -----------------------
    private val adsStorage: HashMap<String, BaseAds<*>> = HashMap()
    
    fun initAdapterInterstitialAds(
        activity: Activity,
        adsId: String,
        eventId: String,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT
    ) {
        if (adsStorage[adsId] != null || _isHideAds) return

        val ads: InterAds<*> = AdapterInterstitialAds(activity, adsId, eventId)
        adsStorage[adsId] = ads

        var retryAttempt = 0.0
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                
                retryAttempt = 0.0
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                
                if (maxRetryAttempt > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        ads.load()
                    }, delayMillis)
                } else {
                    ads.onLoadSuccess()
                    ads.destroyAds()
                }
            }
        })
        ads.load()
    }

    fun showAdapterInterstitialAds(
        loadingTxt: String,
        activity: Activity,
        adsId: String,
        callback: AdsCallback?
    ): Boolean {
        
        val ads = adsStorage[adsId]
        if (ads == null || _isHideAds) {
            callback?.onError("Please initAdapterInterstitialAds() first")
            return false
        }

        if (ads.isLoading()) {
            callback?.onError("Ads are being loading")
            return false
        }

        if (!ads.isAvailable) {
            ads.load()
            callback?.onError("Ads have not been initialized")
            return false
        }

        showAdsWithDialogLoading(activity, ads, callback, loadingTxt)
        return true
    }

    fun clearAdsStorage() {
        adsStorage.clear()
    }

    // ----------------------- Reward -------------------------

    fun initAdapterRewardAds(
        activity: Activity,
        adsId: String,
        eventId: String,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT
    ) {
        if (adsStorage[adsId] != null || _isHideAds) return

        val ads: RewardAds<*> = AdapterRewardAds(activity, adsId, eventId)
        adsStorage[adsId] = ads

        var retryAttempt = 0.0
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                
                retryAttempt = 0.0
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                if (maxRetryAttempt > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        ads.load()
                    }, delayMillis)
                } else {
                    ads.onLoadSuccess()
                    ads.destroyAds()
                }
            }
        })
        ads.load()
    }

    fun showAdapterRewardAds(
        activity: Activity,
        adsId: String,
        callback: AdsCallback?
    ): Boolean {
        val ads = adsStorage[adsId]
        if (ads == null || _isHideAds) {
            callback?.onError("Please initAdapterRewardAds() first")
            return false
        }

        if (ads.isLoading()) {
            callback?.onError("Ads are being loading")
            return false
        }

        if (!ads.isAvailable) {
            ads.load()
            callback?.onError("Ads have not been initialized")
            return false
        }

        showAdsWithDialogLoading(activity, ads, callback)
        return true
    }

    // ----------------------- Banner -----------------------
    
    fun initAdapterBannerAds(
        activity: Activity,
        adId: String,
        eventId: String,
        size: AdSize? = null,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT
    ) {
        if (_isHideAds) return
        
        if (adsStorage[adId] != null) {
            adsStorage[adId]?.destroyAds()
        }
    
        val ads = AdapterBannerAds(
            activity = activity,
            container = null,
            adId = adId,
            eventId = eventId,
            adsSize = size,
        )
        
        adsStorage[adId] = ads
        
        var retryAttempt = 0.0
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
                retryAttempt = 0.0
                
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
            }
            
            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                if (maxRetryAttempt > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        ads.load()
                    }, delayMillis)
                } else {
                    ads.onLoadSuccess()
                    ads.destroyAds()
                }
            }
        })
        ads.load()
    }
    
    fun showAdapterBannerAds(
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        size: AdSize? = null,
        callback: AdsCallback?,
        collapsibleType: String? = null,
        isShowAdsWhenLoaded: Boolean = true
    ) {
        if (_isHideAds) {
            container.visibility = View.GONE
            return
        }
        
        if (!isNetworkAvailable(activity)) {
            callback?.onError("The device is not connected to the internet")
            Log.d("BannerAdmob", "The device is not connected to the internet")
            return
        }
    
        val preloadAds = adsStorage[adId]
        if ((preloadAds as? AdapterBannerAds) != null && preloadAds.isAvailable) {
            preloadAds.show(container, callback)
            return
        }

        val ads = AdapterBannerAds(
            activity = activity,
            container = container,
            adId = adId,
            eventId = eventId,
            adsSize = size,
            collapsibleType = collapsibleType,
            isShowAdsWhenLoaded = isShowAdsWhenLoaded
        )

        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                if (activity.isFinishing || activity.isDestroyed) {
                    ads.destroyAds()
                }
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                callback?.onError(message)
            }
        })
        ads.load()?.show(callback)
    }
    
    fun showMRECsAds(
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        callback: AdsCallback?,
        isAutoRefresh: Boolean = true,
        isShowAdsWhenLoaded: Boolean = true
    ) {
        if (_isHideAds) {
            container.visibility = View.GONE
            return
        }
        
        if (!isNetworkAvailable(activity)) {
            callback?.onError("The device is not connected to the internet")
            container.visibility = View.GONE
        }
        
        val ads = AdapterMRECsAds(
            activity = activity,
            container = container,
            adId = adId,
            eventId = eventId,
            isShowAdsWhenLoaded = isShowAdsWhenLoaded
        )
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
                
                if (isAutoRefresh) ads.startAutoRefresh()
                else ads.stopAutoRefresh()
                
                if (activity.isFinishing || activity.isDestroyed) {
                    ads.destroyAds()
                }
            }
            
            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
                
                callback?.onError(message)
            }
        })
        ads.load()?.show(callback)
    }

    // ---------------------- Native -------------------------
    val adapterNativeAdsViewsStorage: HashMap<String, CustomAdapterNativeAdViews> = HashMap()
    val styleNativeAdsStorage: HashMap<Int, Int> = HashMap()

    fun resetStyleNativeList() {
        styleNativeAdsStorage.clear()

        styleNativeAdsStorage[NativeStyle.BIG_1] = R.layout.ads_native_big_1
        styleNativeAdsStorage[NativeStyle.BIG_2] = R.layout.ads_native_big_2
        styleNativeAdsStorage[NativeStyle.BIG_3] = R.layout.ads_native_big_3
        styleNativeAdsStorage[NativeStyle.BIG_4] = R.layout.ads_native_big_4
        styleNativeAdsStorage[NativeStyle.BIG_5] = R.layout.ads_native_big_5
        styleNativeAdsStorage[NativeStyle.BIG_6] = R.layout.ads_native_big_6
        styleNativeAdsStorage[NativeStyle.BIG_7] = R.layout.ads_native_big_7
        styleNativeAdsStorage[NativeStyle.BIG_8] = R.layout.ads_native_big_8
        styleNativeAdsStorage[NativeStyle.BIG_9] = R.layout.ads_native_big_9
        styleNativeAdsStorage[NativeStyle.BIG_10] = R.layout.ads_native_big_10
        styleNativeAdsStorage[NativeStyle.BIG_11] = R.layout.ads_native_big_11
        styleNativeAdsStorage[NativeStyle.BIG_12] = R.layout.ads_native_big_12
        styleNativeAdsStorage[NativeStyle.MEDIUM_21] = R.layout.ads_native_medium_21
        styleNativeAdsStorage[NativeStyle.MEDIUM_22] = R.layout.ads_native_medium_22
        styleNativeAdsStorage[NativeStyle.SMALL_41] = R.layout.ads_native_small_41
        styleNativeAdsStorage[NativeStyle.SMALL_42] = R.layout.ads_native_small_42
        styleNativeAdsStorage[NativeStyle.SMALL_43] = R.layout.ads_native_small_43
        styleNativeAdsStorage[NativeStyle.SMALL_44] = R.layout.ads_native_small_44
    }

    fun addStyleNative(
        style: Int,
        @LayoutRes layoutAdId: Int,
        override: Boolean = false
    ): Boolean {
        if (style == NativeStyle.PRELOAD) return false
        if (!override && styleNativeAdsStorage[style] != null) return false
        styleNativeAdsStorage[style] = layoutAdId
        return true
    }

    fun initAdapterNativeAds(
        context: Context,
        activity: Activity,
        adsId: String,
        eventId: String,
        style: Int,
        preloads: Int = 1
    ) {
        if (adapterNativeAdsViewsStorage[adsId] != null || _isHideAds) return

        val layoutAdId = styleNativeAdsStorage[style] ?: return

        adapterNativeAdsViewsStorage[adsId] = CustomAdapterNativeAdViews(
            isLoading = true,
            layoutAdId = layoutAdId,
            preloads = preloads,
            listNativeAdView = arrayListOf()
        )

        preloadAdapterNativeAds(context, activity, adsId, layoutAdId, null, eventId)
    }

    fun showAdapterNativeAds(
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        callback: AdsCallback?,
        delayTime: Int = 0,
        style: Int = NativeStyle.SMALL_41,
    ) {
        if (_isHideAds) {
            container.visibility = View.GONE
            return
        }
        
//        if (!isNetworkAvailable(activity)) {
//            callback?.onError("The device is not connected to the internet")
//            container.visibility = View.GONE
//            return
//        }

        val nativeAd = adapterNativeAdsViewsStorage[adId]
        nativeAd?.container = container
        if (nativeAd == null || (nativeAd.listNativeAdView.isEmpty() && !nativeAd.isLoading)) {
            val layoutAdId = styleNativeAdsStorage[style] ?: return
            
            Log.d(TAG, "NativeAdAdapter List is empty -> reload new ads: $adId")
            if (nativeAd == null) {
                adapterNativeAdsViewsStorage[adId] = CustomAdapterNativeAdViews(
                    isLoading = true,
                    layoutAdId = layoutAdId,
                    preloads = 1,
                    listNativeAdView = arrayListOf(),
                    container = container
                )
            }
            
//            val shimmer = createShimmer(activity, layoutAdId)
//            shimmer.startShimmer()
//            container.removeAllViews()
//            container.addView(shimmer)
            container.visibility = View.GONE
            preloadAdapterNativeAds(activity, activity, adId, layoutAdId, container, eventId)
            return
        }
        
        Log.d("NativeAdAdapter", "show ads: $adId")
        showAdsWithShimmer(
            activity,
            adId,
            nativeAd,
            callback,
            container,
            nativeAd.layoutAdId,
            eventId,
            delayTime.toLong()
        )
    }

    fun removeAdapterNativeAds(adId: String) {
        val nativeAd = adapterNativeAdsViewsStorage[adId]
        nativeAd?.listNativeAdView?.forEach {
            if (it.adapterNativeAdView?.parent != null) {
                (it.adapterNativeAdView?.parent as FrameLayout?)?.removeView(it.adapterNativeAdView)
            }
        }
    }

    fun clearadapterNativeAdsViewsStorage() {
        adapterNativeAdsViewsStorage.clear()
    }
    
    fun showAdapterNativeAds(
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        style: Int,
        callback: AdsCallback?,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT,
        isShowAdsWhenLoaded: Boolean = true
    ) {
        if (_isHideAds) {
            container.visibility = View.GONE
            return
        }

        if (!isNetworkAvailable(activity)) {
            callback?.onError("The device is not connected to the internet")
            container.visibility = View.GONE
        }

        val layoutAdId = styleNativeAdsStorage[style]

        if (layoutAdId == null) {
            callback?.onError("Invalid layoutAdId")
            container.visibility = View.GONE
            return
        }

        val admobNativeAds = AdapterNativeAds(
            activity,
            activity,
            container,
            layoutAdId,
            adId,
            eventId
        )
        var retryAttempt = 0.0
        admobNativeAds.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                if (isShowAdsWhenLoaded && isNetworkAvailable(activity)) {
                    container.visibility = View.VISIBLE
                }

                retryAttempt = 0.0
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                if (maxRetryAttempt > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (activity.isDestroyed || activity.isFinishing) {
                            admobNativeAds.destroyAds()
                            return@postDelayed
                        }
                        admobNativeAds.load()
                    }, delayMillis)
                } else {
                    admobNativeAds.onLoadSuccess()
                    admobNativeAds.destroyAds()

                    container.visibility = View.GONE
                    callback?.onError(message)
                }
            }
        })
        admobNativeAds.enableShimmer(layoutAdId)
        admobNativeAds.load()?.show(callback)
    }

    fun clearAllAdsStorage() {
        clearAdsStorage()
        clearadapterNativeAdsViewsStorage()
        resetStyleNativeList()
    }

    fun initAdsAdapter(
        context: Context,
        listTestDeviceId: List<String> = listOf(),
        @DrawableRes ctaBackgroundResource: Int,
        callback: (() -> Unit)? = null
    ) {
        removeCacheMaxAds(context)

        //https://arjun30.medium.com/webview-data-directory-for-android-9-pie-d9744a1404e9
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName(context)
            if (process!= null && context.packageName != process) WebView.setDataDirectorySuffix(process)
        }
        
        this._ctaBgRes = ctaBackgroundResource
    
        AppLovinPrivacySettings.setIsAgeRestrictedUser(false, context)
    
        //Set whether or not user has provided consent for information sharing.
        AppLovinPrivacySettings.setHasUserConsent(true, context)
    
        //Set whether or not user has opted out of the sale of their personal information.
        AppLovinPrivacySettings.setDoNotSell(false, context)
        
        MobileAds.initialize(context) {}
        // Initialize the AppLovin SDK
        AppLovinSdk.getInstance(context).mediationProvider = AppLovinMediationProvider.MAX
        AppLovinSdk.getInstance(context).settings.testDeviceAdvertisingIds = listTestDeviceId
        AppLovinSdk.getInstance(context).initializeSdk {
            callback?.invoke()
        }
//        MobileAds.setRequestConfiguration(
//            RequestConfiguration.Builder().setTestDeviceIds(
//                listOf("B3F63ED7B6AF803B7F44FC2E10DC2672")
//            ).build()
//        )
    }

    fun showAdInspectorDebug(context: Context) {
        AppLovinSdk.getInstance(context).showMediationDebugger()
    }

    fun logFirebaseEvent(event: String, param: Bundle? = null){
        if (event.isNotBlank()) analytics.logEvent(event, param)
    }
    
    fun logRevenueEvent(ad: MaxAd){
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, ad.adUnitId)
            putString(FirebaseAnalytics.Param.AD_FORMAT, ad.format.label)
            putString(FirebaseAnalytics.Param.AD_SOURCE, ad.networkName)
            putDouble(FirebaseAnalytics.Param.VALUE, ad.revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, "USD")
        }

        analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
    }

    private fun getProcessName(context: Context): String? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager?
        for (processInfo in manager?.runningAppProcesses ?: listOf()) {
            if (processInfo.pid == Process.myPid()) {
                return processInfo.processName
            }
        }
        return null
    }

    private fun showAdsWithShimmer(
        activity: Activity,
        adsId: String,
        customAdapterNativeAdViews: CustomAdapterNativeAdViews,
        callback: AdsCallback?,
        container: FrameLayout,
        layoutAdId: Int,
        eventId: String,
        delayTime: Long
    ) {
//        val shimmer = createShimmer(activity, layoutAdId)
//        shimmer.startShimmer()
//        container.removeAllViews()
//        container.addView(shimmer)
        
        removeShimmerAndShowAds(
            activity,
            adsId,
            customAdapterNativeAdViews,
            container,
            eventId,
            callback
        )
    }

    private fun removeShimmerAndShowAds(
        activity: Activity,
        adsId: String,
        customAdapterNativeAdViews: CustomAdapterNativeAdViews,
        container: FrameLayout,
        eventId: String,
        callback: AdsCallback?
    ) {
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }

        val listNotDisplay = customAdapterNativeAdViews.listNativeAdView.filter {
            !it.isDisplayed && it.adapterNativeAdView != null && it.adapterNativeAdView!!.parent == null
        }
    
        Log.i("NativeAdAdapter", "$adsId listNotDisplay size: ${listNotDisplay.size} - ${customAdapterNativeAdViews.listNativeAdView.size}")
        val layoutAdId = customAdapterNativeAdViews.layoutAdId
        if (listNotDisplay.isEmpty()) {
            if (customAdapterNativeAdViews.isLoading || customAdapterNativeAdViews.shouldNotCached()) {
                Log.i("NativeAdAdapter", "no need load cached ads")
            } else {
                Log.i("NativeAdAdapter", "Load new ads and show")
                customAdapterNativeAdViews.isLoading = true
                customAdapterNativeAdViews.preloads++
                container.visibility = View.GONE
                preloadAdapterNativeAds(activity, activity, adsId, layoutAdId, container, eventId)
            }
        } else {
            listNotDisplay.forEach {
                if (it.adapterNativeAdView != null && it.adapterNativeAdView!!.parent == null) {
                    it.isDisplayed = true
                    container.removeAllViews()
                    container.addView(it.adapterNativeAdView)
                    container.visibility = View.VISIBLE
                    
                    if (listNotDisplay.size == 1 && !customAdapterNativeAdViews.isLoading &&
                        layoutAdId != styleNativeAdsStorage[NativeStyle.BIG_2]) {
                        customAdapterNativeAdViews.isLoading = true
                        customAdapterNativeAdViews.preloads++

                        preloadAdapterNativeAds(activity, activity, adsId, customAdapterNativeAdViews.layoutAdId, null, eventId)
                    }
                    callback?.onShow()
                    return
                } else if (it.adapterNativeAdView == null) {
                    Log.i("NativeAdAdapter", "set displayed and continue!!!")
                    it.isDisplayed = true
                } else {
                    Log.i("NativeAdAdapter", "Continue!!!")
                }
            }
        }
    }

    private fun preloadAdapterNativeAds(context: Context, activity: Activity, adsId: String, layoutAdId: Int, container: FrameLayout?, eventId: String) {
        val admobNativeAds = AdapterNativeAds(context, activity, container, layoutAdId, adsId, eventId)
        var retryAttempt = 0.0
        admobNativeAds.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(context, "$eventId loaded success")
                }
                retryAttempt = 0.0
                if (adapterNativeAdsViewsStorage[adsId]?.listNativeAdView != null
                    && adapterNativeAdsViewsStorage[adsId]!!.listNativeAdView.size < adapterNativeAdsViewsStorage[adsId]!!.preloads
                ) {
                    Log.i("NativeAdAdapter", "Reload cached ads after success loaded")
                    reloadNativeAds()
                } else {
                    adapterNativeAdsViewsStorage[adsId]?.isLoading = false
                }
            }

            override fun onLoadFailed(message: String?) {
                if (_enableDebug) {
                    showMessage(context, "Error: $message")
                }
                retryAttempt++
                Log.i("NativeAdAdapter", "Retry ads after load failed")
                reloadNativeAds()
            }

            private fun reloadNativeAds() {
                val delayMillis = TimeUnit.SECONDS.toMillis(
                    2.0.pow(
                        6.0.coerceAtMost(retryAttempt)
                    ).toLong()
                )

                Handler(Looper.getMainLooper()).postDelayed({
                    admobNativeAds.load()
                }, delayMillis)
            }
        })
        admobNativeAds.load()
    }

    private fun createShimmer(activity: Activity, layoutAdId: Int): ShimmerFrameLayout {
        val shimmerBuilder = Shimmer.AlphaHighlightBuilder()
            .setClipToChildren(true)
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        val shimmer = ShimmerFrameLayout(activity)
        shimmer.id = View.generateViewId()
        shimmer.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        val view = activity.layoutInflater.inflate(layoutAdId, shimmer)
        view.setBackgroundResource(R.drawable.bg_ads)
        try {
            val icAd = view.findViewById<TextView>(R.id.ic_ad)
            icAd.text = ""
            icAd.setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            val headline = view.findViewById<TextView>(R.id.ad_headline)
            headline.text = ""
            headline.setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            val body = view.findViewById<TextView>(R.id.ad_body)
            body.text = ""
            body.setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            val advertiser = view.findViewById<TextView>(R.id.ad_advertiser)
            advertiser.text = ""
            advertiser.setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            view.findViewById<ImageView>(R.id.ad_app_icon)
                .setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            view.findViewById<FrameLayout>(R.id.ad_media)
                .setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            view.findViewById<FrameLayout>(R.id.ad_options_view)
                .setBackgroundResource(R.color.lightTransparent)
        } catch (_: Exception) {
        }
        try {
            val call_to_action = view.findViewById<Button>(R.id.ad_call_to_action)
            call_to_action.setBackgroundResource(R.color.lightTransparent)
            call_to_action.backgroundTintList = null
            call_to_action.text = ""
        } catch (_: Exception) {
        }
        shimmer.setShimmer(shimmerBuilder.build())

        return shimmer
    }

    private fun removeCacheMaxAds(context: Context) {
        val folderFileAl = File(context.filesDir.path, "al")
        if (folderFileAl.exists() && folderFileAl.listFiles() != null) {
            folderFileAl.listFiles()!!.forEach {
                if (it.name != ".nomedia" && it.name != "persistent_postback_cache.json") {
                    try {
                        it.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun showAdsWithDialogLoading(
        activity: Activity,
        ads: BaseAds<*>,
        callback: AdsCallback?,
        loadingTxt: String = ""
    ) {
        val dialogLoading = DialogAdsLoading(activity, loadingTxt)
        dialogLoading.show()
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isDestroyed || activity.isFinishing) {
                return@postDelayed
            }
            dialogLoading.cancel()
            ads.show(callback)
        }, 1000)
    }

    companion object {
        private const val TAG = "multiple_mediation"

        const val DEFAULT_MAX_RETRY_ATTEMPT = 6

        private var INSTANCE: CoreAds? = null

        @JvmStatic
        val instance: CoreAds
            get() {
                if (INSTANCE == null) {
                    synchronized(CoreAds::class.java) {
                        if (INSTANCE == null) {
                            INSTANCE = CoreAds()
                            INSTANCE!!.resetStyleNativeList()
                        }
                    }
                }
                return INSTANCE!!
            }

        @JvmStatic
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
    
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
    
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        }
    }
}
