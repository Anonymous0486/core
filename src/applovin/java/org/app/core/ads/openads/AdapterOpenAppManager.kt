package org.app.core.ads.openads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.google.firebase.analytics.FirebaseAnalytics
import org.app.core.ads.CoreAds
import org.app.core.ads.dialog.DialogInterOpenAdsLoading
import org.app.core.ads.remoteconfig.type.OpenType
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class AdapterOpenAppManager private constructor() : LifecycleObserver,
    ActivityLifecycleCallbacks {
    private var appOpenAd: MaxAppOpenAd? = null
    private var interstitialAd: MaxInterstitialAd? = null

    private var currentActivity: Activity? = null
    private var adId: String? = null
    private var isShowingAd = false
    private var isLoadingAd = false
    private var disableOpenAdsList: MutableList<Class<*>>? = null
    private var openAdsEnable = true
    private var retryAttempt = 0.0
    private var callback: Callback? = null
    private var isOpenType: String? = OpenType.OPEN

    fun setOpenType(@OpenType openType: String) {
        isOpenType = openType
        if (openType == OpenType.OPEN) {
            initOpenAds()
            loadOpenAds()
        } else {
            initInterAds()
            loadInterAds()
        }
    }

    fun registerLifecycle(application: Application, adId: String?, callback: Callback) {
        this.adId = adId
        this.isOpenType = OpenType.OPEN
        this.callback = callback
        application.registerActivityLifecycleCallbacks(this)
        disableOpenAdsList = ArrayList()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setOpenAdsId(adId: String?) {
        this.adId = adId
        onStart()
    }

    /**
     * LifecycleObserver methods
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (CoreAds.instance.isHideAds) return
        
        if (!CoreAds.isNetworkAvailable(currentActivity!!)) {
            Log.d(TAG, "MaxOpenApp cannot show because device is not connected to the internet")
            return
        }

        callback?.initAds(currentActivity!!)

        if (isAdOtherClicked) {
            isAdOtherClicked = false
            return
        }

        if (isAdOtherShowFullScreen) {
            return
        }
        showAds()
    }

    private fun showAds() {
        if (isLoadingAd) {
            Log.d(TAG, "MaxOpenApp cannot show because ads are being loading")
            return
        }
        if (isShowingAd) {
            Log.d(TAG, "MaxOpenApp cannot show because ads are being showing")
            return
        }

        when (isOpenType) {
            OpenType.OPEN -> {
                if (appOpenAd == null) {
                    if (adId == null || adId!!.isEmpty() || currentActivity == null) return
                    initOpenAds()
                }

                if (appOpenAd!!.isReady) {
                    if (inDisableOpenAdsList() || !openAdsEnable) return
                    appOpenAd!!.showAd()
                } else {
                    Log.d(TAG, "MaxOpenApp cannot show because ads have just started loading")
                    loadOpenAds()
                }
            }
            OpenType.INTER -> {
                if (interstitialAd == null) {
                    if (adId == null || adId!!.isEmpty() || currentActivity == null) return
                    initInterAds()
                }
                if (interstitialAd!!.isReady) {
                    if (inDisableOpenAdsList() || !openAdsEnable || currentActivity == null) return

                    val dialogLoading = DialogInterOpenAdsLoading(currentActivity!!)
                    dialogLoading.show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentActivity == null
                            || currentActivity!!.isDestroyed
                            || currentActivity!!.isFinishing
                        ) return@postDelayed
                        dialogLoading.cancel()
                        interstitialAd!!.showAd()
                    }, 1500)

                } else {
                    Log.d(TAG, "MaxOpenApp cannot show because ads have just started loading")
                    loadInterAds()
                }
            }
            else -> {
                if (adId == null || adId!!.isEmpty()) return
                Log.d(TAG, "MaxOpenApp cannot show. Please set setOpenType()")
            }
        }

    }

    private fun initOpenAds() {
        appOpenAd = MaxAppOpenAd(adId ?: return, currentActivity ?: return)
        retryAttempt = 0.0
        initOpenAdListener()
    }

    private fun initInterAds() {
        interstitialAd = MaxInterstitialAd(adId!!, currentActivity!!)
        retryAttempt = 0.0
        initInterAdListener()
    }

    private fun initOpenAdListener() {
        appOpenAd?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)

            Log.d("AdRevenue", "-------------------------------------------")
            Log.d("AdRevenue", "MaxOpenApp Revenue: " + ad.revenue)
            Log.d("AdRevenue", "MaxOpenApp NetworkName: " + ad.networkName)
            Log.d("AdRevenue", "MaxOpenApp AdUnitId: " + ad.adUnitId)
            Log.d("AdRevenue", "MaxOpenApp Placement: " + ad.placement)
            Log.d("AdRevenue", "-------------------------------------------")
        }
        appOpenAd?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdLoaded")
                isLoadingAd = false
                retryAttempt = 0.0
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdDisplayed")
                isShowingAd = true
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdHidden")
                isShowingAd = false

                if (appOpenAd == null) {
                    if (adId == null || adId!!.isEmpty() || currentActivity == null) return
                    initOpenAds()
                }
                loadOpenAds()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdClicked")
                CoreAds.instance.logFirebaseEvent("ClickSwitchApp")

            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG, "MaxOpenApp onAdLoadFailed: ${error.message}")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (appOpenAd == null) {
                            if (adId == null || adId!!.isEmpty() || currentActivity == null) return@postDelayed
                            initOpenAds()
                        }
                        loadOpenAds()
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                }
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG, "MaxOpenApp onAdDisplayFailed: ${error.message}")
                isShowingAd = false
            }
        })
    }

    private fun initInterAdListener() {
        interstitialAd?.setRevenueListener { ad ->
            CoreAds.instance.logRevenueEvent(ad)
            Log.d("AdRevenue", "-------------------------------------------")
            Log.d("AdRevenue", "MaxOpenApp Revenue: " + ad.revenue)
            Log.d("AdRevenue", "MaxOpenApp NetworkName: " + ad.networkName)
            Log.d("AdRevenue", "MaxOpenApp AdUnitId: " + ad.adUnitId)
            Log.d("AdRevenue", "MaxOpenApp Placement: " + ad.placement)
            Log.d("AdRevenue", "-------------------------------------------")
        }
        interstitialAd?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdLoaded")
                isLoadingAd = false
                retryAttempt = 0.0
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdDisplayed")
                isShowingAd = true
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdHidden")
                isShowingAd = false

                if (appOpenAd == null) {
                    if (adId == null || adId!!.isEmpty() || currentActivity == null) return
                    initInterAds()
                }
                loadInterAds()
            }

            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "MaxOpenApp onAdClicked")
                CoreAds.instance.logFirebaseEvent("ClickSwitchApp")
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG, "MaxOpenApp onAdLoadFailed: ${error.message}")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (appOpenAd == null) {
                            if (adId == null || adId!!.isEmpty() || currentActivity == null) return@postDelayed
                            initInterAds()
                        }
                        loadInterAds()
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                }
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG, "MaxOpenApp onAdDisplayFailed: ${error.message}")
                isShowingAd = false
            }
        })
    }

    private fun loadOpenAds() {
        appOpenAd?.loadAd() ?: return
        isLoadingAd = true
    }

    private fun loadInterAds() {
        interstitialAd?.loadAd() ?: return
        isLoadingAd = true
    }

    fun registerDisableOpenAdsAt(cls: Class<*>?) {
        if (cls != null) {
            disableOpenAdsList!!.add(cls)
        }
    }

    fun removeDisableOpenAdsAt(cls: Class<*>) {
        disableOpenAdsList!!.remove(cls)
    }

    private fun inDisableOpenAdsList(): Boolean {
        return disableOpenAdsList!!.contains(currentActivity!!.javaClass)
    }

    fun disableOpenAds() {
        openAdsEnable = false
    }

    fun enableOpenAds() {
        openAdsEnable = true
    }


    // Activity lifecycle
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        currentActivity = null
    }

    companion object {
        private const val DEFAULT_MAX_RETRY_ATTEMPT = 6

        private const val TAG = "proxads"

        var isAdOtherShowFullScreen: Boolean = false
        var isAdOtherClicked: Boolean = false

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AdapterOpenAppManager? = null

        @JvmStatic
        val instance: AdapterOpenAppManager
            get() {
                synchronized(AdapterOpenAppManager::class.java) {
                    if (INSTANCE == null) {
                        synchronized(AdapterOpenAppManager::class.java) {
                            INSTANCE = AdapterOpenAppManager()
                        }
                    }
                }
                return INSTANCE!!
            }
    }

    interface Callback {
        fun initAds(activity: Activity)
    }
}