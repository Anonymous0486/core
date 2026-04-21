package org.app.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
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
import androidx.core.view.isEmpty
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.MobileAds
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.app.core.R
import org.app.core.ads.base.BaseAds
import org.app.core.ads.base.CollapsibleType
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.base.RewardAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LaunchingCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.dialog.DialogAdsLoading
import org.app.core.ads.nativeads.AdapterNativeAdView
import org.app.core.ads.nativeads.AdapterNativeAds
import org.app.core.ads.nativeads.AdmobNativeAds
import org.app.core.ads.nativeads.CustomAdapterNativeAdViews
import org.app.core.ads.openads.AdapterOpenAds
import org.app.core.ads.openads.AdapterOpenAppManager
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.utils.showMessage
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.pow

@SuppressLint("LogNotTimber")
class CoreAds private constructor(
    private val appContext: Context
) {
    private var analytics: FirebaseAnalytics = Firebase.analytics
    private var _enableDebug = false
    private var _initialized = false

    var bannerContainer: FrameLayout?
        get() = _bannerContainer
        set(value) {
            _bannerContainer = value
        }
    private var _bannerContainer: FrameLayout? = null
    
    @DrawableRes
    private var _ctaBgRes : Int? = null
    
    val ctaBackgroundDrawable: Int?
        get() = _ctaBgRes
    
    private var _userConsent = true
    var userConsent: Boolean
        get() = _userConsent
        set(value) { _userConsent = value }
    
    private var _isHideAds = false
    val isHideAds: Boolean
        get() = _isHideAds || !_userConsent

    private var _lastFullAdsTime = 0L
    var lastFullAdsTime: Long
        get() = _lastFullAdsTime
        set(value) { _lastFullAdsTime = value }

    //TODO: New functions for new concept load and show native ads
    private val _nativeAdsList = mutableListOf<AdmobNativeAds>()
    private val _nativeLoadedTs = MutableStateFlow(0L)      // Timestamp saved of last native ads loaded
    val nativeLoadedTs = _nativeLoadedTs.asStateFlow()

    fun updateTimestamp(timestamp: Long) {
        if (_isHideAds) return

        _nativeLoadedTs.tryEmit(timestamp)
    }

    fun releaseNativeAds(requestId: String) {
        if (requestId.isBlank()) return

        val ads = _nativeAdsList.firstOrNull { it.requestId == requestId }
        if (ads != null && ads.isDisplayed()) {
            _nativeAdsList.remove(ads)
        }
    }

    fun toggleDebug(mode: Boolean? = null) : Boolean {
        _enableDebug = mode ?: !_enableDebug
        
        return _enableDebug
    }

    fun setHideAds(flag: Boolean) {
        _isHideAds = flag
        Timber.tag(TAG).i("setHideAds: $flag")
        if (_isHideAds) {
            AdapterOpenAppManager.instance.disableOpenAds()
        } else {
            AdapterOpenAppManager.instance.enableOpenAds()
        }
    }

    fun initLaunchingAds(
        isFirst: Boolean,
        activity: Activity,
        loadingTxt: String,
        container: FrameLayout,
        callback: LaunchingCallback?
    ) {
        var isTimeout = false
        var status = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            if (status == ALL_DONE) return@Runnable
            if (activity.isDestroyed || activity.isFinishing) return@Runnable

            isTimeout = true
            callback?.onCompleted()
        }
        handler.postDelayed(runnable, if (isFirst) 8000 else 15000)
        val rmc = CoreRemoteConfig.instance.adsRemoteConfig
        if (rmc != null) {
            val splashAds = rmc.natives?.firstOrNull {
                it.tag == "SplashActivity_Native" && !it.id.isNullOrBlank()
            }
            if (splashAds != null) {
                val adsId = splashAds.id!!
                admobNativeAdsViewsStorage[adsId] = CustomAdapterNativeAdViews(
                    isLoading = false,
                    preloads = 1,
                    size = 1,
                )
                val layoutAdId = splashAds.style ?: 13
                val shimmer = createShimmer(activity, layoutAdId)
                shimmer.startShimmer()
                container.removeAllViews()
                container.addView(shimmer)

                val admobNativeAds = AdapterNativeAds(
                    activity.applicationContext,
                    null,
                    layoutAdId,
                    adsId,
                    splashAds.event ?: "SplashNativeDummy",
                    false
                )

                admobNativeAds.setLoadCallback(object : LoadCallback() {
                    override fun onLoadSuccess() {
                        super.onLoadSuccess()
                        if (!isTimeout) {
                            admobNativeAdsViewsStorage[adsId]?.nativeAds?.poll()?.let { nativeAd ->
                                val adsView =  nativeAd.populateNativeAdView(layoutAdId, activity, container)
                                admobNativeAdsViewsStorage[adsId]?.lastDisplayAds = nativeAd
                                container.removeAllViews()
                                container.addView(adsView)
                            }

                            status = status.or(NATIVE_DONE)
                            if (status == ALL_DONE) {
                                Handler(Looper.getMainLooper()).postDelayed( {
                                    callback?.onCompleted()
                                }, 1000)
                            }
                        }

                        callback?.onLoaded()
                    }

                    override fun onLoadFailed(message: String?) {
                        if (!isTimeout) {
                            status = status.or(NATIVE_DONE)
                            if (status == ALL_DONE) {
                                callback?.onCompleted()
                            }
                        }
                    }
                })
                admobNativeAds.load()
            }

            if (isFirst) {
                val guideAds = rmc.interstitials?.firstOrNull {
                    it.tag == "Guide" && !it.id.isNullOrBlank()
                }
                if (guideAds != null) {
                    val ads = AdapterInterstitialAds(activity, guideAds.id!!, guideAds.event ?: "GuideDummy")
                    adsInterStorage[guideAds.id!!] = ads

                    ads.setLoadCallback(object : LoadCallback() {
                        override fun onLoadSuccess() {
                            super.onLoadSuccess()

                            status = status.or(INTER_DONE)
                            if (status == ALL_DONE) {
                                callback?.onCompleted()
                            }
                        }

                        override fun onLoadFailed(message: String?) {
                            super.onLoadFailed(message)
                            status = status.or(INTER_DONE)
                            if (status == ALL_DONE) {
                                callback?.onCompleted()
                            }
                        }
                    })
                    ads.load()
                } else {
                    status = status.or(INTER_DONE)
                    if (status == ALL_DONE) {
                        callback?.onCompleted()
                    }
                }
            } else {
                val startAppAds = rmc.splash?.firstOrNull {
                    it.version == rmc.active_version && !it.id.isNullOrBlank()
                }
                if (startAppAds != null) {
                    val interAds = AdapterInterstitialAds(
                        context = appContext,
                        adId = startAppAds.id!!,
                        eventId = startAppAds.event ?: "StartAppDummy",
                        tag = "AdmobInterstitialSplash"
                    )

                    interAds.setLoadCallback(object : LoadCallback() {
                        override fun onLoadSuccess() {
                            super.onLoadSuccess()
                            status = status.or(INTER_DONE)

                            if (isTimeout) {
                                adsInterStorage[startAppAds.id!!] = interAds
                                return
                            }

                            if (activity.isDestroyed || activity.isFinishing) {
                                adsInterStorage[startAppAds.id!!] = interAds
                                callback?.onCompleted()
                                return
                            }

                            interAds.turnOffAutoReload()
                            val dialogLoading = DialogAdsLoading(activity, loadingTxt)
                            dialogLoading.show()
                            showAdsWithDialogLoading(activity,
                                interAds,
                                object : AdsCallback() {
                                    override fun onClosed() {
                                        super.onClosed()
                                        callback?.onCompleted()
                                    }

                                    override fun onError(message: String?) {
                                        super.onError(message)
                                        callback?.onCompleted()
                                    }
                                },
                                dialogLoading
                            )
                        }

                        override fun onLoadFailed(message: String?) {
                            super.onLoadFailed(message)

                            status = status.or(INTER_DONE)
                            if (status == ALL_DONE) {
                                callback?.onCompleted()
                            }
                        }
                    }).load()
                } else {
                    status = status.or(INTER_DONE)
                    if (status == ALL_DONE) {
                        callback?.onCompleted()
                    }
                }
            }
        } else {
            isTimeout = true
            callback?.onCompleted()
        }
    }
    
    fun showAdapterInterstitialSplashAds(
        loadingTxt: String,
        activity: Activity,
        adsId: String,
        eventId: String,
        timeout: Int,
        callback: AdsCallback?
    ) {
        if (_isHideAds) {
            callback?.onClosed()
            return
        }
        val splashAds = AdapterInterstitialAds(context = appContext, adId = adsId, eventId = eventId, tag = "AdmobInterstitialSplash")
        var splashDone = false

        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            Timber.tag("AdmobInterstitialSplash").i("Timeout...$splashDone")
            if (splashDone) return@Runnable
            splashDone = true

            logFirebaseEvent(eventId + "_timeout")
            if (activity.isDestroyed || activity.isFinishing) return@Runnable
            callback?.onError("Timeout")
        }
        handler.postDelayed(runnable, timeout.toLong())
        splashAds.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()

                if (_enableDebug) {
                    showMessage(appContext, "$eventId loaded success")
                }
                splashAds.turnOffAutoReload()
                if (splashDone) {
                    return
                }
                splashDone = true
                handler.removeCallbacksAndMessages(null)
                if (activity.isDestroyed || activity.isFinishing) {
                    logFirebaseEvent(eventId + "_Actv_Hidden")
                    adsInterStorage[adsId] = splashAds
                    return
                }

                Timber.tag("AdmobInterstitialSplash").i("Show after loaded...")
                val dialogLoading = DialogAdsLoading(activity, loadingTxt)
                dialogLoading.show()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (dialogLoading.isShowing == true) dialogLoading.cancel()
                    }catch (_: Exception){}

                    if (activity.isDestroyed || activity.isFinishing) {
                        logFirebaseEvent( "AppDummy_Actv_Hidden")
                        adsInterStorage[adsId] = splashAds
                        return@postDelayed
                    }
                    splashAds.show(activity, callback)
                }, 1000)
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)

                if (_enableDebug) {
                    showMessage(appContext, "Error: $message")
                }
                if (activity.isDestroyed || activity.isFinishing) return
                callback?.onError(message)
            }
        }).load()
    }

    fun showAdapterOpenSplashAds(
        activity: Activity,
        adsId: String,
        eventId: String,
        timeout: Int,
        callback: AdsCallback?
    ) {
        var splashAds: AdapterOpenAds? =
            AdapterOpenAds(context = appContext, adId = adsId,  eventId, tag = "AdmobOpenSplash")
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
    private val adsInterStorage: HashMap<String, AdapterInterstitialAds> = HashMap()
    
    fun initAdapterInterstitialAds(
        activity: Activity,
        adsId: String,
        eventId: String,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT
    ) {
        
        if (isHideAds) return
        
        if (adsInterStorage[adsId] != null &&
            (adsInterStorage[adsId]?.isAvailable == true || adsInterStorage[adsId]?.isLoading() == true)) return
        
        Log.d(TAG, "Start loading Inter in Init: $adsId")
        val ads = AdapterInterstitialAds(activity, adsId, eventId)
        adsInterStorage[adsId] = ads

        var retryAttempt = 1.0
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()
    
                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                
                retryAttempt = 1.0
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                
                if (message != null && (message.contains("no fill")
                            || message.contains("No fill"))) {
                    ads.destroyAds()
                    return
                } else {
                    if (maxRetryAttempt > retryAttempt) {
                        retryAttempt++
                        val delayMillis = TimeUnit.SECONDS.toMillis(
                            2.0.pow(
                                6.0.coerceAtMost(retryAttempt)
                            ).toLong() + 1
                        )
                        
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "Reload Inter in Init: $adsId")
                            ads.load()
                        }, delayMillis)
                    } else {
                        ads.destroyAds()
                    }
                }
            }
        })
        ads.load()
    }

    fun showAdapterInterstitialAds(
        timelapse: Long = 40000,
        loadingTxt: String,
        activity: Activity,
        adsId: String,
        eventId: String,
        callback: AdsCallback?
    ): Boolean {
        if (isHideAds) {
            callback?.onClosed()
            if (adsInterStorage[adsId]?.isAvailable == true) {
                logFirebaseEvent(eventId + "_not_consent")
            }
            return false
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime < (_lastFullAdsTime + timelapse)) {
            logFirebaseEvent(eventId + "_to_fast")
            callback?.onClosed()
            return false
        }

        val dialogLoading = DialogAdsLoading(activity, loadingTxt)
        dialogLoading.show()
        var asdCompleted = false
        
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable {
            Log.i(TAG, "Inter timeout loading: $asdCompleted -> $adsId")
            
            if (asdCompleted) return@Runnable
            asdCompleted = true

            try {
                if (dialogLoading.isShowing) {
                    dialogLoading.cancel()
                }
            }catch (_: Exception) {}

            logFirebaseEvent(eventId + "_timeout")
            if (activity.isDestroyed || activity.isFinishing) return@Runnable
            callback?.onError("Ad request timed out")
        }
        handler.postDelayed(runnable, 15000)
        
        val ads = if (adsInterStorage[adsId] == null) {
            var cacheAds: AdapterInterstitialAds? = null
            for (m in adsInterStorage) {
                if (m.value.isAvailable) {
                    cacheAds = adsInterStorage.remove(m.key)
                    break
                }
            }

            if (cacheAds == null) {
                Log.i(TAG, "Init New Inter in show: $adsId")
                val newAds = AdapterInterstitialAds(appContext, adsId, eventId)
                adsInterStorage[adsId] = newAds

                newAds
            } else {
                Log.i(TAG, "Cached Inter for id: $adsId")
                logFirebaseEvent(eventId + "_cachedAds")
                cacheAds
            }
        } else {
            Log.i(TAG, "Existed Inter in show: $adsId")
            adsInterStorage[adsId]!!
        }
        
        ads.turnOnAutoReload()
        if (ads.isLoading() || !ads.isAvailable) {
            Log.i(TAG, "Load or waiting Inter in show: $adsId -> ${ads.isLoading()}")
            ads.setLoadCallback(object : LoadCallback() {
                override fun onLoadSuccess() {
                    super.onLoadSuccess()

                    try {
                        if (dialogLoading.isShowing) {
                            dialogLoading.cancel()
                        }
                    } catch (_: Exception) {}

                    if (_enableDebug) {
                        showMessage(appContext, "$adsId loaded success")
                    }
                    ads.turnOffAutoReload()
                    asdCompleted = true
                    handler.removeCallbacksAndMessages(null)
                    if (activity.isDestroyed || activity.isFinishing) {
                        adsInterStorage[adsId] = ads
                        logFirebaseEvent(eventId + "_Actv_Hidden")
                        return
                    }
                    
                    Log.i(TAG, "Show when loaded in case re-init: $adsId")
                    ads.show(activity, callback)
                }
                
                override fun onLoadFailed(message: String?) {
                    super.onLoadFailed(message)
                    
                    if (_enableDebug) {
                        showMessage(appContext, "Error: $message")
                    }

                    try {
                        if (dialogLoading.isShowing) {
                            dialogLoading.cancel()
                        }
                    } catch (_: Exception) {}
                    
                    asdCompleted = true
                    handler.removeCallbacksAndMessages(null)
                    callback?.onError("Error: $message")
                }
            })
            
            if (!ads.isLoading()) {
                ads.load()
            }
            
            return true
        }
        
        Log.i(TAG, "Show inter: $adsId")
        Handler(Looper.getMainLooper()).postDelayed({
            handler.removeCallbacksAndMessages(null)
            
            if (activity.isDestroyed || activity.isFinishing) {
                try {
                    if (dialogLoading.isShowing) dialogLoading.cancel()
                }catch (_: Exception){}

                logFirebaseEvent(eventId + "_Actv_Hidden")
                return@postDelayed
            }
            
            asdCompleted = true
            ads.turnOffAutoReload()
            ads.show(activity, callback)
            try {
                if (dialogLoading.isShowing) dialogLoading.cancel()
            }catch (_: Exception){}
        }, 1000)

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
        if (adsStorage[adsId] != null) return

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
        eventId: String,
        loadingTxt: String,
        callback: AdsCallback?
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime < (_lastFullAdsTime + 30000)) {
            logFirebaseEvent(eventId + "_to_fast")
            callback?.onClosed()
            return false
        }

        val ads: RewardAds<*> = if (adsStorage[adsId] == null) {
            AdapterRewardAds(appContext, adsId, eventId)
        } else {
            adsStorage[adsId] as? RewardAds<*> ?: AdapterRewardAds(appContext, adsId, eventId)
        }

        adsStorage[adsId] = ads
        if (ads.isAvailable) {
            showAdsWithDialogLoading(activity, ads, callback)
            return true
        } else {
            Timber.tag("AdapterRewardAds").i("Load or waiting reward in show: $adsId -> ${ads.isLoading()}")
            val dialogLoading = DialogAdsLoading(activity, loadingTxt)
            dialogLoading.show()
            var asdCompleted = false

            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                Log.i(TAG, "reward timeout loading: $asdCompleted -> $adsId")

                if (asdCompleted) return@Runnable
                asdCompleted = true

                try {
                    if (dialogLoading.isShowing) {
                        dialogLoading.cancel()
                    }
                }catch (_: Exception) {}

                logFirebaseEvent(eventId + "_timeout")
                if (activity.isDestroyed || activity.isFinishing) return@Runnable
                callback?.onError("Ad request timed out")
            }
            handler.postDelayed(runnable, 15000)
            ads.setLoadCallback(object : LoadCallback() {
                override fun onLoadSuccess() {
                    super.onLoadSuccess()

                    try {
                        if (dialogLoading.isShowing) {
                            dialogLoading.cancel()
                        }
                    } catch (_: Exception) {}

                    if (_enableDebug) {
                        showMessage(appContext, "$adsId loaded success")
                    }
                    ads.turnOffAutoReload()
                    asdCompleted = true
                    handler.removeCallbacksAndMessages(null)
                    if (activity.isDestroyed || activity.isFinishing) {
                        logFirebaseEvent(eventId + "_Actv_Hidden")
                        return
                    }

                    Log.i(TAG, "Show when loaded in case re-init: $adsId")
                    ads.show(activity, callback)
                    _lastFullAdsTime = System.currentTimeMillis()
                }

                override fun onLoadFailed(message: String?) {
                    super.onLoadFailed(message)

                    if (_enableDebug) {
                        showMessage(appContext, "Error: $message")
                    }

                    try {
                        if (dialogLoading.isShowing) {
                            dialogLoading.cancel()
                        }
                    } catch (_: Exception) {}

                    asdCompleted = true
                    handler.removeCallbacksAndMessages(null)
                    callback?.onError("Error: $message")
                }
            })

            if (!ads.isLoading()) {
                ads.load()
            }
            return false
        }
    }

    // ----------------------- Banner -----------------------
    
    fun initAdapterBannerAds(
        adId: String,
        eventId: String,
        size: AdSize? = null,
        collapsibleType: String? = null,
        maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT
    ) {
       if (isHideAds) return

        val key = adId + if (size == null) "_default" else "_$size"
        if (adsStorage[key] != null && adsStorage[key]?.isAvailable == true) {
            return
        }

        Timber.tag("BannerAdmob").i("Load and show -> $collapsibleType")
        val ads = AdapterBannerAds(
            context = appContext,
            container = null,
            adId = adId,
            eventId = eventId,
            adsSize = size,
            collapsibleType = collapsibleType,
        )
        
        var retryAttempt = 0.0
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()

                adsStorage[key] = ads
                retryAttempt = 0.0
                if (_enableDebug) {
                    showMessage(appContext, "$eventId loaded success")
                }
            }
            
            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)
    
                if (_enableDebug) {
                    showMessage(appContext, "Error: $message")
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
        collapsibleType: String? = null,
        loadCallback: LoadCallback?,
    ) : AdapterBannerAds? {
        val key = adId + if (size == null) "_default" else "_$size"

        Timber.tag("BannerAdmob").i( "Show BannerAdmob: $key")
        val preloadAds = adsStorage[key]
        if (isHideAds) {
            container.visibility = View.GONE
            if (preloadAds?.isAvailable == true) {
                logFirebaseEvent(eventId + "_not_consent")
            }
            return null
        }

        if ((preloadAds as? AdapterBannerAds) != null) {
            if (preloadAds.isReady()) {
                Log.i("BannerAdmob", "Show ready ads...")

                preloadAds.show(container, null)
                if ((collapsibleType != CollapsibleType.TOP
                            && collapsibleType != CollapsibleType.BOTTOM)
                    && preloadAds.isFirstDisplay) {

                    return preloadAds
                }
            } else {
                if (preloadAds.isLoading()) {
                    Log.i("BannerAdmob", "Waiting ads load...")
                    if (loadCallback != null) {
                        preloadAds.setLoadCallback(loadCallback)
                    }
                    if (container.isEmpty()) {
                        preloadAds.showShimmer(size, container)
                    }
                    return null
                }
            }
        }

        Timber.tag("BannerAdmob").i("Load and show $collapsibleType")
        val isCollapsible = collapsibleType == CollapsibleType.TOP || collapsibleType == CollapsibleType.BOTTOM
        val ads = AdapterBannerAds(
            context = if (isCollapsible) activity else appContext,
            container = null,
            adId = adId,
            eventId = eventId,
            adsSize = size,
            collapsibleType = collapsibleType,
        )

        if (container.isEmpty()) {
            ads.showShimmer(size, container)
        }

        if (!isNetworkAvailable(activity)) {
            Timber.tag("BannerAdmob").i("The device is not connected to the internet")
            return null
        }

        adsStorage[key] = ads
        if (loadCallback != null) {
            ads.setLoadCallback(loadCallback)
        }
        
        ads.load()
        if ((preloadAds as? AdapterBannerAds) != null) {
            if (preloadAds.isReady()) {
                return preloadAds
            }
        }
        return null
    }

    fun showAvailableBanner(
        container: FrameLayout,
        adId: String,
        eventId: String,
        size: AdSize? = null,
    ) {
        val key = adId + if (size == null) "_default" else "_$size"

        Timber.tag("BannerAdmob").i( "Show BannerAdmob: $key -> $isHideAds")
        val preloadAds = adsStorage[key]
        if (isHideAds) {
            container.visibility = View.GONE
            if (preloadAds?.isAvailable == true) {
                logFirebaseEvent(eventId + "_not_consent")
            }
            return
        }

        if ((preloadAds as? AdapterBannerAds) != null) {
            if (preloadAds.isReady()) {
                Timber.tag("BannerAdmob").i( "Show ready ads...")
                preloadAds.show(container, null)
            }
        }
    }

    fun showLockScreenBannerAds(
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        size: AdSize? = null,
        callback: AdsCallback?,
        collapsibleType: String? = null,
    ) : AdapterBannerAds? {
        val key = adId + if (size == null) "_default" else "_$size"

        Timber.tag("BannerAdmob").i("Show BannerAdmob: $key")
        val preloadAds = adsStorage[key]
        if (isHideAds) {
            container.visibility = View.GONE
            return null
        }

        if ((preloadAds as? AdapterBannerAds) != null && preloadAds.isAvailable) {
            Timber.tag("BannerAdmob").i("Show LockScreen ads with first display ${preloadAds.isFirstDisplay}")
            preloadAds.show(container, callback)

            if (preloadAds.isLoading()) {
                Timber.tag("BannerAdmob").i("Show shimmer while loading...")
                return null
            } else {
                if ((collapsibleType != CollapsibleType.TOP
                            && collapsibleType != CollapsibleType.BOTTOM)
                    && preloadAds.isFirstDisplay) {

                    return preloadAds
                }
            }
        }

        if (!isNetworkAvailable(activity)) {
            callback?.onError("The device is not connected to the internet")
            Timber.tag("BannerAdmob").i("The device is not connected to the internet")
            return null
        }
        Log.i("BannerAdmob", "Load and show $collapsibleType")
        val ads = AdapterBannerAds(
            context = appContext,
            container = null,
            adId = "ca-app-pub-6445739239297382/6346653244",
            eventId = eventId + "_BA",
            adsSize = size,
            collapsibleType = null,
            isShowAdsWhenLoaded = true,
        )

        if (container.isEmpty()) {
            ads.showShimmer(size, container)
        }
        _bannerContainer = container
        ads.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()

                if (_enableDebug) {
                    showMessage(activity, "$eventId loaded success")
                }
                try {
                    if (_bannerContainer != null) {
                        ads.show(_bannerContainer!!, null)
                    }
                } catch (_: Exception) { }
            }

            override fun onLoadFailed(message: String?) {
                super.onLoadFailed(message)

                if (_enableDebug) {
                    showMessage(activity, "Error: $message")
                }
                callback?.onError(message)
            }
        })

        ads.load()
        adsStorage[key] = ads
        return null
    }

    // ----------------------- MRECs -----------------------

    // ---------------------- Native -------------------------
    val admobNativeAdsViewsStorage: HashMap<String, CustomAdapterNativeAdViews> = HashMap()
    var nativeContainer: FrameLayout? = null

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
        styleNativeAdsStorage[NativeStyle.BIG_13] = R.layout.ads_native_big_13
        styleNativeAdsStorage[NativeStyle.BIG_14] = R.layout.ads_native_big_14
        styleNativeAdsStorage[NativeStyle.MEDIUM_21] = R.layout.ads_native_medium_21
        styleNativeAdsStorage[NativeStyle.MEDIUM_22] = R.layout.ads_native_medium_22
        styleNativeAdsStorage[NativeStyle.SMALL_41] = R.layout.ads_native_small_41
        styleNativeAdsStorage[NativeStyle.SMALL_42] = R.layout.ads_native_small_42
        styleNativeAdsStorage[NativeStyle.SMALL_43] = R.layout.ads_native_small_43
        styleNativeAdsStorage[NativeStyle.SMALL_44] = R.layout.ads_native_small_44
        styleNativeAdsStorage[NativeStyle.FULLSCREEN] = R.layout.ads_native_full
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
    
    fun initAdapterNativeAdsMultiple(
        context: Context,
        activity: Activity,
        adsId: String,
        eventId: String,
        style: Int,
        preloads: Int = 1,
        isOnce: Boolean = false,
        container: FrameLayout? = null,
    ) {
        if (isHideAds) return

        if (admobNativeAdsViewsStorage[adsId] != null &&
            admobNativeAdsViewsStorage[adsId]!!.nativeAds.isNotEmpty() &&
            admobNativeAdsViewsStorage[adsId]!!.nativeAds.size >= preloads
            ) {
            return
        }
        
        val layoutAdId = styleNativeAdsStorage[style] ?: return
        
        if (!isNetworkAvailable(context)) return

        if (admobNativeAdsViewsStorage[adsId] == null) {
            admobNativeAdsViewsStorage[adsId] = CustomAdapterNativeAdViews(
                isLoading = true,
                preloads = preloads,
                size = preloads,
                isOnce = isOnce,
            )
        } else {
            admobNativeAdsViewsStorage[adsId]!!.preloads = preloads
            admobNativeAdsViewsStorage[adsId]!!.isOnce = isOnce
        }
        
        Log.i(TAG, "NativeAdmob: start multiple preload ads -> $adsId")

        if (container != null) {
            val shimmer = createShimmer(activity, layoutAdId)
            shimmer.startShimmer()
            container.removeAllViews()
            container.addView(shimmer)
        }
        val admobNativeAds = AdapterNativeAds(context, container, layoutAdId, adsId, eventId, false)
        admobNativeAds.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                super.onLoadSuccess()

                if (_enableDebug) {
                    showMessage(context, "$eventId loaded success")
                }

                if (!isOnce && admobNativeAdsViewsStorage[adsId]?.nativeAds != null &&
                    admobNativeAdsViewsStorage[adsId]?.nativeAds!!.size < admobNativeAdsViewsStorage[adsId]!!.preloads) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        admobNativeAdsViewsStorage[adsId]?.isLoading = true
                        admobNativeAds.load()
                    }, 1000)
                } else {
                    admobNativeAdsViewsStorage[adsId]?.isLoading = false
                }
            }

            override fun onLoadFailed(message: String?) {
                if (_enableDebug) {
                    showMessage(context, "Error: $message")
                }
            }
        })
        admobNativeAds.load()
    }
    
    fun showAdapterNativeAdsMultiple(
        context: Context,
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        style: Int = NativeStyle.SMALL_41,
        hideInLoading: Boolean = true,
        preloads: Int = 1,
        callback: AdsCallback? = null,
    ) {
        val layoutAdId = styleNativeAdsStorage[style] ?: return
        val customNativeAd = admobNativeAdsViewsStorage[adId]
        if (isHideAds) {
            container.visibility = View.GONE
            if (customNativeAd != null && !customNativeAd.nativeAds.isEmpty()) {
                logFirebaseEvent(eventId + "_not_consent")
            }
            return
        }

        if (customNativeAd == null || (customNativeAd.nativeAds.isEmpty() && !customNativeAd.isLoading)) {
            if (customNativeAd == null) {
                val size = max(preloads, 1)
                admobNativeAdsViewsStorage[adId] = CustomAdapterNativeAdViews(
                    isLoading = false,
                    preloads = size,
                    size = size,
                    isOnce = preloads == 0
                )
            } else {
                admobNativeAdsViewsStorage[adId]?.preloads = 1
            }
            
            val aView = findAnyNative()
            if (aView != null) {
                Timber.tag(TAG).i("NativeAdmob: Empty -> show any ads")
                admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
                admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null

                aView.showAdView(layoutAdId, context, container)
                admobNativeAdsViewsStorage[adId]?.lastDisplayAds = aView

                callback?.onShow()

                if (preloads > 0) {
                    multiplePreloadAdapterNativeAds(context, activity, adId, layoutAdId, null, eventId)
                    logFirebaseEvent(eventId + "_preload")
                }
            } else {
                Timber.tag(TAG).i("NativeAdmob: Empty -> Load and show ads: $adId - $preloads")
                val lastAds = admobNativeAdsViewsStorage[adId]?.lastDisplayAds
                
                if (lastAds != null) {
                    Timber.tag(TAG).i("NativeAdmob: Show last display ads!!")
                    lastAds.showAdView(layoutAdId, context, container)

                    if (preloads == 0) {
                        return
                    }
                } else {
                    val lastAnyAds = findLastDisplayAds()
                    if (lastAnyAds != null) {
                        lastAnyAds.showAdView(layoutAdId, context, container)
                        if (preloads == 0) {
                            return
                        }
                    } else {
                        if (hideInLoading) {
                            container.visibility = View.GONE
                        } else {
                            Timber.tag(TAG).i("NativeAdmob: child count -> ${container.childCount}")
                            if (container.childCount == 0) {
                                val shimmer = createShimmer(activity, layoutAdId)
                                shimmer.startShimmer()
                                container.removeAllViews()
                                container.addView(shimmer)
                            }
                        }
                    }
                }
                if (isNetworkAvailable(context)) {
                    logFirebaseEvent(eventId + "_LoadAndShow")
                    admobNativeAdsViewsStorage[adId]?.isLoading = true
                    admobNativeAdsViewsStorage[adId]?.isOnce = preloads == 0
                    multiplePreloadAdapterNativeAds(
                        context,
                        activity,
                        adId,
                        layoutAdId,
                        null,
                        eventId
                    )
                }
            }
            
            return
        }

        admobNativeAdsViewsStorage[adId]?.isOnce = preloads == 0
        showMultiplePreloadAds(
            context,
            activity,
            adId,
            layoutAdId,
            customNativeAd,
            container,
            eventId,
            hideInLoading,
            callback
        )
    }

    fun showAdapterNativeAdsIfAvailable(
        context: Context,
        activity: Activity,
        container: FrameLayout,
        adId: String,
        eventId: String,
        style: Int = NativeStyle.SMALL_41,
    ) : Boolean {
        val layoutAdId = styleNativeAdsStorage[style] ?: return false
        val customNativeAd = admobNativeAdsViewsStorage[adId]
        if (isHideAds) {
            container.visibility = View.GONE
            if (customNativeAd != null && !customNativeAd.nativeAds.isEmpty()) {
                logFirebaseEvent(eventId + "_not_consent")
            }
            return false
        }
        if (activity.isDestroyed || activity.isFinishing) {
            return false
        }

        if (customNativeAd == null || customNativeAd.nativeAds.isEmpty()) {
            return false
        }
        val nativeAd = customNativeAd.nativeAds.poll() ?: return false
        nativeAd.showAdView(layoutAdId, context, container)

        admobNativeAdsViewsStorage[adId]?.lastDisplayAds?.nativeAd?.destroy()
        admobNativeAdsViewsStorage[adId]?.lastDisplayAds = null
        admobNativeAdsViewsStorage[adId]?.lastDisplayAds = nativeAd

        return true
    }

    fun preloadAdmobNativeAds(
        context: Context,
        adId: String,
        eventId: String,
    ) {
        if (_nativeAdsList.isNotEmpty()) {
            val ads = _nativeAdsList.firstOrNull { it.isAvailable() || it.isLoading() }
            if (ads != null) {
                //TODO: Has at least 1 available ads or loading ads
                return
            }
        }

        Timber.tag(TAG).d("Native ads preload...")
        val aNative = AdmobNativeAds(context = context, adUnitId = adId, event = eventId)
        aNative.setDisplayWhenLoaded(false)
        _nativeAdsList.add(aNative)
        aNative.loadAds()
    }

    fun showAdmobNativeAds(
        container: FrameLayout?,
        style: Int = NativeStyle.SMALL_41
    ) : AdmobNativeAds? {
        val layoutAdId = styleNativeAdsStorage[style] ?: return null
        val loadedAds = _nativeAdsList.firstOrNull { it.isAvailable() }
        if (loadedAds != null) {
            if (container != null) {
                Timber.tag(TAG).d("NativeAdmob show available ads")
                loadedAds.showAdView(layoutAdId, appContext, container)
            }
            return loadedAds
        }
        return null
    }

    fun loadOrShowAdmobNativeAds(
        container: FrameLayout?,
        adId: String,
        eventId: String,
        style: Int = NativeStyle.SMALL_41,
        callback: LoadCallback? = null,
    ) : AdmobNativeAds? {
        val layoutAdId = styleNativeAdsStorage[style] ?: return null
        if (_nativeAdsList.isEmpty()) {
            Timber.tag(TAG).d("NativeAdmob is empty -> request to load...")
            val aNative = AdmobNativeAds(context = appContext, adUnitId = adId, event = eventId)
            if (container != null) {
                callback?.let { aNative.setLoadCallback(callback) }
                Timber.tag(TAG).d("NativeAdmob is empty -> start shimmer")
                val shimmer = aNative.createShimmer(appContext, layoutAdId)
                shimmer.startShimmer()
                container.removeAllViews()
                container.addView(shimmer)
            }
            _nativeAdsList.add(aNative)
            aNative.loadAds()
            return null
        }

        val loadedAds = _nativeAdsList.firstOrNull { it.isAvailable() }
        if (loadedAds != null) {
            if (container != null) {
                Timber.tag(TAG).d("NativeAdmob show available ads")
                loadedAds.showAdView(layoutAdId, appContext, container)
            }
            return loadedAds
        }

        val loadingAds = _nativeAdsList.firstOrNull { it.isLoading() }
        if (loadingAds != null) {
            Timber.tag(TAG).d("NativeAdmob ads is loading -> show waiting shimmer")
            loadingAds.setDisplayWhenLoaded(true)
            if (container != null && container.isEmpty()) {
                callback?.let { loadingAds.setLoadCallback(callback) }
                val shimmer = loadingAds.createShimmer(appContext, layoutAdId)
                shimmer.startShimmer()
                container.removeAllViews()
                container.addView(shimmer)
            }
        } else {
            Timber.tag(TAG).d("NativeAdmob ads refresh the new ads...")
            _nativeAdsList.removeIf { it.isDisplayed() }

            val aNative = AdmobNativeAds(context = appContext, adUnitId = adId, event = eventId)
            if (container != null && container.isEmpty()) {
                Timber.tag(TAG).d("NativeAdmob ads is empty -> start shimmer")
                callback?.let { aNative.setLoadCallback(callback) }
                val shimmer = aNative.createShimmer(appContext, layoutAdId)
                shimmer.startShimmer()
                container.removeAllViews()
                container.addView(shimmer)
            } else {
                Timber.tag(TAG).d("NativeAdmob ads is empty -> just load new ads")
            }
            _nativeAdsList.add(aNative)
            aNative.loadAds()
        }

        return null
    }

    fun removeContainerBy(adId: String) {
        admobNativeAdsViewsStorage[adId]?.nativeAds?.forEach { ads ->
            ads.container = null
        }
    }

    fun clearAdmobNativeAdsViewsStorage() {
        admobNativeAdsViewsStorage.clear()
    }

    fun clearAllAdsStorage() {
        clearAdsStorage()
        clearAdmobNativeAdsViewsStorage()
        resetStyleNativeList()
    }

    @SuppressLint("LogNotTimber")
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

        val configurationBuilder =
            MobileAds.getRequestConfiguration().toBuilder()
        configurationBuilder.setTestDeviceIds(listTestDeviceId)
//        configurationBuilder.setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
        MobileAds.setRequestConfiguration(configurationBuilder.build())

        CoroutineScope(Dispatchers.IO).launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(context) {
                    initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                for (adapterClass in statusMap.keys) {
                    val status = statusMap[adapterClass]
                    Log.d(
                        TAG,
                        String.format(
                            "Adapter name: %s, Description: %s, Latency: %d",
                            adapterClass,
                            status!!.description,
                            status.latency
                        )
                    )
                }
                (context as? Activity)?.runOnUiThread {
                    callback?.invoke()
                } ?: kotlin.run { callback?.invoke() }
            }
            _initialized = true
            MobileAds.setAppVolume(0.5f)
        }
    }
    
    fun ensureAdapterInitialized(
        context: Context,
        @DrawableRes ctaBackgroundResource: Int,
    ) {
        if (!_initialized) {
            initAdsAdapter(
                context = context,
                TEST_DEVICE_IDS,
                ctaBackgroundResource = ctaBackgroundResource
            )
        }
    }

    fun showAdInspectorDebug(context: Context) {
        MobileAds.openAdInspector(context) {}
    }

    fun logFirebaseEvent(event: String, param: Bundle? = null){
        if (event.isNotBlank()) analytics.logEvent(event, param)
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

    private fun multiplePreloadAdapterNativeAds(context: Context, activity: Activity, adsId: String, layoutAdId: Int, container: FrameLayout?, eventId: String) {
        val admobNativeAds = AdapterNativeAds(context, container, layoutAdId, adsId, eventId, false)
        
        //TODO: May need handle load callback later
        admobNativeAds.setLoadCallback(object : LoadCallback() {
            override fun onLoadSuccess() {
                if (_enableDebug) {
                    showMessage(context, "$eventId loaded success")
                }
                //TODO: Apply new concept that should not preload ads
//                val preload = admobNativeAdsViewsStorage[adsId]?.preloads ?: 0
//                val size = admobNativeAdsViewsStorage[adsId]?.nativeAds?.size ?: 0
//
//                if (admobNativeAdsViewsStorage[adsId]?.nativeAds != null &&
//                    (size == 0 || size < min(preload, 2))  &&
//                    admobNativeAdsViewsStorage[adsId]?.isOnce == false
//                ) {
//                    Handler(Looper.getMainLooper()).postDelayed({
//                        admobNativeAdsViewsStorage[adsId]?.isLoading = true
//                        admobNativeAds.load()
//                    }, 1000)
//                } else {
//                    admobNativeAdsViewsStorage[adsId]?.isLoading = false
//                }
                admobNativeAdsViewsStorage[adsId]?.isLoading = false
            }
            
            override fun onLoadFailed(message: String?) {
                if (_enableDebug) {
                    showMessage(context, "Error: $message")
                }
                admobNativeAdsViewsStorage[adsId]?.isLoading = false
            }
        })
        admobNativeAds.load()
    }
    
    private fun showMultiplePreloadAds(
        context: Context,
        activity: Activity,
        adsId: String,
        layoutAdId: Int,
        customNativeAd: CustomAdapterNativeAdViews,
        container: FrameLayout,
        eventId: String,
        hideInLoading: Boolean = true,
        callback: AdsCallback?
    ) {
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }
        
        val nativeAd = customNativeAd.nativeAds.poll()
        if (nativeAd == null) {
            val aView = findAnyNative()
            if (aView != null) {
                Timber.tag(TAG).i("NativeAdmob Show ads found in all native!!!")
                admobNativeAdsViewsStorage[adsId]?.lastDisplayAds?.nativeAd?.destroy()
                admobNativeAdsViewsStorage[adsId]?.lastDisplayAds = null

                val adsView =  aView.populateNativeAdView(layoutAdId, context, container)
                admobNativeAdsViewsStorage[adsId]?.lastDisplayAds = aView
                container.removeAllViews()
                container.addView(adsView)
                callback?.onShow()
            } else {
                if (hideInLoading) {
                    container.visibility = View.GONE
                } else {
                    Timber.tag(TAG).i("NativeAdmob No ads -> add shimmer ${container.childCount == 0} for waiting ads load: $adsId")
                    if (container.childCount == 0) {
                        val shimmer = createShimmer(activity, layoutAdId)
                        shimmer.startShimmer()
                        container.removeAllViews()
                        container.addView(shimmer)
                    }
                }
            }
            Timber.tag(TAG).i("NativeAdmob is on loading ${customNativeAd.isLoading}")
            if (!customNativeAd.isLoading && isNetworkAvailable(context)) {
                Timber.tag(TAG).i("NativeAdmob [Multiple] No ads -> start load for show: $adsId")
                customNativeAd.isLoading = true
                admobNativeAdsViewsStorage[adsId]?.preloads = 0
                multiplePreloadAdapterNativeAds(context, activity, adsId, layoutAdId, null, eventId)
            }
        } else {
            Timber.tag(TAG).i("NativeAdmob [Multiple] Show ads: $adsId")
            admobNativeAdsViewsStorage[adsId]?.lastDisplayAds?.nativeAd?.destroy()
            admobNativeAdsViewsStorage[adsId]?.lastDisplayAds = null
            // TODO: Apply should not preload ads
//            if (customNativeAd.nativeAds.size == 0 &&
//                !customNativeAd.isLoading &&
//                !customNativeAd.isOnce &&
//                isNetworkAvailable(context)) {
//
//                Log.i("NativeAdmob", "Load more ads for cached!!")
//                admobNativeAdsViewsStorage[adsId]?.preloads = 1
//                multiplePreloadAdapterNativeAds(context, activity, adsId, layoutAdId, null, eventId)
//            }
            
            val adsView =  nativeAd.populateNativeAdView(layoutAdId, context, container)
            admobNativeAdsViewsStorage[adsId]?.lastDisplayAds = nativeAd
            container.removeAllViews()
            container.addView(adsView)
            callback?.onShow()
        }
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
        dialogLoading: DialogAdsLoading? = null
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isDestroyed || activity.isFinishing) {
                try {
                    if (dialogLoading?.isShowing == true) dialogLoading.cancel()
                }catch (_: Exception){}
                logFirebaseEvent( "AppDummy_Actv_Hidden")
                callback?.onError("Activity finished")
                return@postDelayed
            }
            ads.show(activity, callback)
            try {
                if (dialogLoading?.isShowing == true) dialogLoading.cancel()
            }catch (_: Exception){}
        }, 1000)
    }
    
    private fun findAnyNative() : AdapterNativeAdView? {
        for ((k, value) in admobNativeAdsViewsStorage) {
            if (value.nativeAds.isNotEmpty()) {
                return admobNativeAdsViewsStorage[k]?.nativeAds?.poll()
            }
        }
        
        return null
    }

    private fun findLastDisplayAds() : AdapterNativeAdView? {
        for ((k, value) in admobNativeAdsViewsStorage) {
            if (value.lastDisplayAds != null) {
                return admobNativeAdsViewsStorage[k]?.lastDisplayAds
            }
        }

        return null
    }

    companion object {
        private const val TAG = "multiple_mediation"

        const val DEFAULT_MAX_RETRY_ATTEMPT = 1

        const val NATIVE_DONE = 1       // 0x01
        const val INTER_DONE = 2        // 0x10
        const val ALL_DONE = 3          // 0x11

        @Volatile
        private var INSTANCE: CoreAds? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = CoreAds(context.applicationContext)
                        INSTANCE!!.resetStyleNativeList()
                    }
                }
            }
        }

        val instance: CoreAds
            get() = INSTANCE
                ?: throw IllegalStateException("CoreAds not initialized")

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
