package org.app.core.ads.openads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.app.core.ads.CoreAds
import org.app.core.ads.dialog.DialogInterOpenAdsLoading
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.remoteconfig.type.OpenType
import org.app.core.feature.CoreFeature
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow

@SuppressLint("LogNotTimber")
class AdapterOpenAppManager private constructor() : LifecycleObserver,
    Application.ActivityLifecycleCallbacks {
    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null

    private var myApplication: Application? = null
    private var currentActivity: Activity? = null
    private var adId: String? = null
    private var isShowingAd = false
    private var isLoadingAd = false
    private var disableOpenAdsList: MutableList<Class<*>>? = null
    private var openAdsEnable = true
    private var retryAttempt = 0.0
    private var loadTime: Long = 0
    private var callback: Callback? = null
    private var isOpenType: String? = OpenType.OPEN
    private var isStopped: Boolean = false
    private var launchingCallback: (() -> Unit)? = null
    private var isLaunching: Boolean = true

    private var showWhenLoaded: Boolean = false
    private var dialogLoading: DialogInterOpenAdsLoading? = null
    private var loadingCallback: (() -> Unit)? = null

    fun setOpenType(@OpenType openType: String) {
        isOpenType = openType
        val rmAds = CoreRemoteConfig.instance.findAppOpenAds()
        if (rmAds != null && rmAds.always_preload == true) {
            if (openType == OpenType.OPEN) {
                loadOpenAds()
            } else {
                loadInterAds()
            }
        }
    }

    fun reloadAdsIfNeed() {
        if (!isAdAvailable()) {
            val rmAds = CoreRemoteConfig.instance.findAppOpenAds()
            if (rmAds != null && rmAds.always_preload == true) {
                Handler(Looper.getMainLooper()).post {
                    if (isOpenType == OpenType.OPEN) {
                        Log.i(TAG, "Reload ads after network is connected: ")
                        loadOpenAds()
                    } else {
                        loadInterAds()
                    }
                }
            }
        }
    }
    
    fun registerLifecycle(application: Application, adId: String?, callback: Callback) {
        this.adId = adId
        this.myApplication = application
        this.callback = callback
        this.isOpenType = OpenType.OPEN
        myApplication?.registerActivityLifecycleCallbacks(this)
        disableOpenAdsList = ArrayList()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Timber.tag(TAG).i( "Init with ads id -> $adId")
    }

    fun setOpenAdsId(adId: String?) {
        if (!adId.isNullOrBlank() && adId != this.adId) {
            Timber.tag(TAG).i("Set id -> $adId")
            this.adId = adId
        }
//        CoreFeature.instance.initializeFeatureBy(myApplication?.packageName ?: "")
    }

    fun preloadAds() {
        if (myApplication == null || adId == null || adId!!.isEmpty() || isLoadingAd) return

        if (!CoreAds.isNetworkAvailable(myApplication!!)) {
            Timber.tag(TAG).i( "Load ads network is not connected!!!")
            return
        }

        val loadCallback = object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Launch_Loaded")
                Timber.tag(TAG).i( "PreloadAds loaded: $isOpenType")
                isLoadingAd = false
                retryAttempt = 0.0
                ad.fullScreenContentCallback = mListener
                ad.onPaidEventListener = onPaidEventListener

                if (showWhenLoaded) {
                    callback?.onAdFullScreenShow(currentActivity)
                    Timber.tag(TAG).i( "PreloadAds Show:-->  $adId ${appOpenAd == null} : ${currentActivity == null}")
                    currentActivity?.let {
                        isLaunching = false
                        CoreAds.instance.lastFullAdsTime = System.currentTimeMillis()
                        ad.show(it)
                    } ?: kotlin.run {
                        launchingCallback?.invoke()
                        launchingCallback = null
                    }
                } else {
                    appOpenAd = ad
                    loadTime = Date().time
                    launchingCallback?.invoke()
                    launchingCallback = null
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Timber.tag(TAG).i( "PreloadAds FailedToLoad: $isOpenType  ${loadAdError.message}")
                CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Launch_Failed")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong() + 10
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (myApplication == null || adId == null || adId!!.isEmpty()) return@postDelayed

                        AppOpenAd.load(
                            myApplication!!,
                            adId!!,
                            AdRequest.Builder().build(),
                            this
                        )
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                    launchingCallback?.invoke()
                    launchingCallback = null
                }
            }
        }

        showWhenLoaded = false
        CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Launch_Request")
        AppOpenAd.load(myApplication!!, adId!!, AdRequest.Builder().build(), loadCallback)
        retryAttempt = 0.0
        isLoadingAd = true
    }

    /**
     * LifecycleObserver methods
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        isStopped = false
        if (CoreAds.instance.isHideAds) return

        Timber.tag(TAG).i( "Init in ON_START $adId - $isAdOtherClicked -> $isAdOtherShowFullScreen")

        if (isAdOtherClicked) {
            isAdOtherClicked = false
            return
        }

        if (isAdOtherShowFullScreen) {
            return
        }

        showAds()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppStopped() {
        isStopped = true
    }

    fun showLaunchingOpenApp(onCompleted: () -> Unit) {
        launchingCallback = onCompleted
        isLaunching = true
        if (isAdAvailable()) {
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Launch_Show")
            if (!isStopped) {
                callback?.onAdFullScreenShow(currentActivity)
                Log.i(TAG, "ShowLaunching OpenApp ads-->  $adId ${appOpenAd == null} : ${currentActivity == null}")
                currentActivity?.let {
                    CoreAds.instance.lastFullAdsTime = System.currentTimeMillis()
                    appOpenAd?.show(it)
                } ?: kotlin.run {
                    onCompleted.invoke()
                }
            }
        } else {
            showWhenLoaded = true
            Log.i(TAG, "Launching waiting...")
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Launch_Wait")
            Handler(Looper.getMainLooper()).postDelayed({
                if (isLaunching) {
                    launchingCallback?.invoke()
                }
            }, 13000)
        }
    }

    fun showLoadingIfNeed(onCompleted: () -> Unit) {
        val rmAds = CoreRemoteConfig.instance.findAppOpenAds()
        Timber.tag(TAG).i( "showLoadingIfNeed: ${rmAds?.always_preload}")
        if (rmAds != null && rmAds.always_preload == true) {
            onCompleted.invoke()
        } else {
            if (dialogLoading == null) {
                dialogLoading = DialogInterOpenAdsLoading(currentActivity!!)
            }
            try {
                dialogLoading?.show()
                loadingCallback = onCompleted
            } catch (e: Exception) {
                e.printStackTrace()
                dialogLoading = null
                loadingCallback = null
            }
        }
    }

    private fun showAds() {
        if (CoreAds.instance.isHideAds) {
            Log.i(TAG, "Currently hide ads")
            return
        }

        if (isLoadingAd) {
            Log.i(TAG, "Cannot show because ads are being loading: $isOpenType")
            return
        }
        if (isShowingAd) {
            Log.i(TAG, "Cannot show because ads are being showing: $isOpenType ")
            return
        }

        when (isOpenType) {
            OpenType.OPEN -> {
                if (inDisableOpenAdsList() || !openAdsEnable) return

                if (isAdAvailable()) {
                    Log.i(TAG, "Start delay for showing ads--> $adId")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isStopped) {
                            callback?.onAdFullScreenShow(currentActivity)
                            Log.i(TAG, "Show ads-->  $adId ${appOpenAd == null} : ${currentActivity == null}")
                            currentActivity?.let { appOpenAd?.show(it) }
                        }
                    }, 500)
                } else {
                    val rmAds = CoreRemoteConfig.instance.findAppOpenAds()
                    Log.i(TAG, "Cannot show because no ads available: ${rmAds?.always_preload}")
                    if (rmAds != null && rmAds.always_preload == true) {
                        loadOpenAds()
                    } else {
                        loadAndShowOpenAds()
                    }
                }
            }
            OpenType.INTER -> {
                if (interstitialAd != null) {
                    if (currentActivity == null) return
                    if (inDisableOpenAdsList() || !openAdsEnable) return
                    val dialogLoading = DialogInterOpenAdsLoading(currentActivity!!)
                    dialogLoading.show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentActivity == null
                            || currentActivity!!.isDestroyed
                            || currentActivity!!.isFinishing) return@postDelayed
                        dialogLoading.cancel()
                        interstitialAd!!.show(currentActivity!!)
                    }, 1500)
                } else {
                    Log.d(TAG, "Cannot show because ads have just started loading")
                    loadInterAds()
                }
            }
            else -> {
                if (myApplication == null || adId == null || adId!!.isEmpty()) return
                Log.d(TAG, "cannot show. Please set setOpenType()")
            }
        }
    }

    private fun loadOpenAds() {
        if (myApplication == null || adId == null || adId!!.isEmpty() || isLoadingAd) return

        if (!CoreAds.isNetworkAvailable(myApplication!!)) {
            Timber.tag(TAG).i( "Load ads network is not connected!!!")
            return
        }

        val loadCallback = object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                Timber.tag(TAG).i( "onAdLoaded: $isOpenType")
                isLoadingAd = false
                retryAttempt = 0.0
                ad.fullScreenContentCallback = mListener
                ad.onPaidEventListener = onPaidEventListener
                appOpenAd = ad
                loadTime = Date().time
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Timber.tag(TAG).i( "onAdFailedToLoad: $isOpenType  ${loadAdError.message}")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong() + 10
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (myApplication == null || adId == null || adId!!.isEmpty()) return@postDelayed

                        AppOpenAd.load(
                            myApplication!!,
                            adId!!,
                            AdRequest.Builder().build(),
                            this
                        )
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                }
            }
        }

        AppOpenAd.load(myApplication!!, adId!!, AdRequest.Builder().build(), loadCallback)
        retryAttempt = 0.0
        isLoadingAd = true
    }

    private fun loadAndShowOpenAds() {
        if (adId == null || adId!!.isEmpty()) {
            adId = CoreRemoteConfig.instance.findAppOpenId()
        }

        if (myApplication == null || adId == null || adId!!.isEmpty() || isLoadingAd) {
            showWhenLoaded = isLoadingAd
            Timber.tag(TAG).i( "Can not show because of null or empty id")
            return
        }

        if (!CoreAds.isNetworkAvailable(myApplication!!)) {
            Timber.tag(TAG).i( "Load ads network is not connected!!!")
            return
        }

        currentActivity ?: return
        if (dialogLoading == null) {
            dialogLoading = DialogInterOpenAdsLoading(currentActivity!!)
        }
        try {
            dialogLoading?.show()
        } catch (e: Exception) {
            e.printStackTrace()
            dialogLoading = null
        }
        val loadCallback = object : AppOpenAdLoadCallback() {

            override fun onAdLoaded(ad: AppOpenAd) {
                Timber.tag(TAG).i( "onAdLoaded: $isOpenType")
                isLoadingAd = false
                retryAttempt = 0.0
                ad.fullScreenContentCallback = mListener
                ad.onPaidEventListener = onPaidEventListener

                try {
                    if (dialogLoading?.isShowing == true) {
                        dialogLoading?.dismiss()
                    }
                    dialogLoading = null
                } catch (_: Exception) {}

                if (showWhenLoaded && !isStopped) {
                    callback?.onAdFullScreenShow(currentActivity)
                    Timber.tag(TAG).i( "Show ads-->  $adId ${appOpenAd == null} : ${currentActivity == null}")
                    currentActivity?.let { ad.show(it) }
                } else {
                    appOpenAd = ad
                    loadTime = Date().time
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Timber.tag(TAG).i( "onAdFailedToLoad: $isOpenType  ${loadAdError.message}")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong() + 10
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (myApplication == null || adId == null || adId!!.isEmpty()) return@postDelayed

                        AppOpenAd.load(
                            myApplication!!,
                            adId!!,
                            AdRequest.Builder().build(),
                            this
                        )
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                }
            }
        }

        CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Request")
        Timber.tag(TAG).i( "Load and show...")
        AppOpenAd.load(myApplication!!, adId!!, AdRequest.Builder().build(), loadCallback)
        retryAttempt = 0.0
        isLoadingAd = true
        showWhenLoaded = true
        Handler(Looper.getMainLooper()).postDelayed({
            showWhenLoaded = false
            try {
                callback?.onAdFullScreenCompleted(currentActivity)
                if (dialogLoading?.isShowing == true) {
                    Timber.tag(TAG).i( "on timeout--->")
                    CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Timeout")
                    dialogLoading?.dismiss()
                    loadingCallback?.invoke()
                    loadingCallback = null
                    dialogLoading = null
                }
            } catch (_: Exception) {}
        }, 8000)
    }

    private fun loadInterAds() {
        if (currentActivity == null || adId == null || adId!!.isEmpty()) return

        val loadCallback = object : InterstitialAdLoadCallback() {

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "onAdLoaded")
                isLoadingAd = false
                retryAttempt = 0.0
                ad.fullScreenContentCallback = mListener
                ad.onPaidEventListener = onPaidEventListener
                interstitialAd = ad
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d(TAG, "onAdFailedToLoad: ${loadAdError.message}")

                if (DEFAULT_MAX_RETRY_ATTEMPT > retryAttempt) {
                    retryAttempt++
                    val delayMillis = TimeUnit.SECONDS.toMillis(
                        2.0.pow(
                            6.0.coerceAtMost(retryAttempt)
                        ).toLong()
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (currentActivity == null || adId == null || adId!!.isEmpty()) return@postDelayed

                        InterstitialAd.load(
                            currentActivity!!,
                            adId!!,
                            AdRequest.Builder().build(),
                            this
                        )
                    }, delayMillis)
                } else {
                    isLoadingAd = false
                    retryAttempt = 0.0
                }
            }
        }

        InterstitialAd.load(currentActivity!!, adId!!, AdRequest.Builder().build(), loadCallback)
        retryAttempt = 0.0
        isLoadingAd = true
    }


    /** Utility method that checks if ad exists and can be shown.  */
    private fun isAdAvailable(): Boolean {
        Timber.tag(TAG).i( "check ads is available -> ${appOpenAd != null}")
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /** Utility method to check if ad was loaded more than n hours ago.  */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    fun registerDisableOpenAdsAt(cls: Class<*>) {
        disableOpenAdsList!!.add(cls)
    }

    fun removeDisableOpenAdsAt(cls: Class<*>) {
        disableOpenAdsList!!.remove(cls)
    }

    private fun inDisableOpenAdsList(): Boolean {
        currentActivity ?: return false
        
        return disableOpenAdsList!!.contains(currentActivity!!.javaClass)
    }

    fun disableOpenAds() {
        Timber.tag(TAG).i( "disableOpenAds--> $adId")
        openAdsEnable = false
    }

    fun enableOpenAds() {
        Timber.tag(TAG).i( "enableOpenAds--> $adId")
        openAdsEnable = true
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
        override fun onAdDismissedFullScreenContent() {
            Timber.tag(TAG).i( "onAdDismissedFullScreenContent")
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Dismiss")
            // Set the reference to null so isAdAvailable() returns false.
            appOpenAd = null
            isShowingAd = false
            callback?.onAdFullScreenCompleted(currentActivity)
            launchingCallback?.invoke()
            launchingCallback = null
            loadingCallback?.invoke()
            loadingCallback = null
            // Temporary ignore preload
            val rmAds = CoreRemoteConfig.instance.findAppOpenAds()
            if (rmAds != null && rmAds.always_preload == true) {
                when (isOpenType) {
                    OpenType.OPEN -> loadOpenAds()
                    OpenType.INTER -> loadInterAds()
                }
            }
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Timber.tag(TAG).i( "onAdFailedToShowFullScreenContent: ${adError.message}")
            isShowingAd = false
            callback?.onAdFullScreenCompleted(currentActivity)
            launchingCallback?.invoke()
            launchingCallback = null
            loadingCallback?.invoke()
            loadingCallback = null
        }

        override fun onAdShowedFullScreenContent() {
            Timber.tag(TAG).i( "onAdShowedFullScreenContent")
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Shown")
            isShowingAd = true
        }

        override fun onAdClicked() {
            Timber.tag(TAG).i( "onAdClicked")
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Clicked")
        }

        override fun onAdImpression() {
            Timber.tag(TAG).i( "onAdImpression")
            CoreAds.instance.logFirebaseEvent("ClickSwitchApp_Impression")
        }
    }

    private inner class AdmobPaidEventCallback : OnPaidEventListener {
        override fun onPaidEvent(adValue: AdValue) {
            Timber.tag(TAG).i( "onPaidEvent")
        }
    }

    // Activity lifecycle
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        Timber.tag(TAG).i( "onActivityStarted ${activity.localClassName}")
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        Timber.tag(TAG).i( "onActivityResumed ${activity.localClassName}")
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        Timber.tag(TAG).i( "onActivityDestroyed ${activity.localClassName}")
    }

    companion object {
        private const val DEFAULT_MAX_RETRY_ATTEMPT = 0

        private const val TAG = "AdmobOpenApp"

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
        fun initAds(activity: Activity?)
        
        fun onAdFullScreenShow(activity: Activity?)
        
        fun onAdFullScreenCompleted(activity: Activity?)
    }
}