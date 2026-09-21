package org.app.core.ads.base

import android.app.Activity
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.app.core.ads.CoreAds
import org.app.core.ads.callback.AdsActionHandler
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.OnViewGlobalLayoutListener
import org.app.core.base.extensions.calculateBannerHeightBy
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.setMargins
import org.app.core.base.extensions.show
import org.app.core.base.utils.StringResId
import org.app.core.base.utils.px
import timber.log.Timber
import kotlin.math.min

class ScreenAdsDelegate(
    private val lifecycleOwnerProvider: () -> LifecycleOwner?,
    private val activityProvider: () -> Activity?,
    private val viewContainerProvider: () -> AdsViewContainer,
    private val screenTagProvider: () -> String,
    private val nativeHeightProvider: () -> Int = { 0 },
    private val minActiveStateProvider: () -> Lifecycle.State = { Lifecycle.State.RESUMED },
    private val initializeLoading: () -> Boolean = { false },
    private val onShowLoading: ((Boolean) -> Unit)? = null
) : AdsActionHandler, DefaultLifecycleObserver {
    private val REFRESH_INTERVAL_MS = 30_000L

    private var _hasNativeAds: Boolean = false
    private var _hasBannerAds: Boolean = false
    private var _firstTimeShownBanner: Boolean = true
    private var _requestNativeId: String = ""
    private var _nativeFullId: String = ""
    private var _refreshJob: Job? = null
    private var _firstDisplay = true
    private var _timeStamp: Long = 0L

    private val minActiveState: Lifecycle.State
        get() = minActiveStateProvider()

    fun attachLifecycle(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        if (CoreAds.instance.isHideAds) {
            _hasNativeAds = false
            _hasBannerAds = false
            viewContainerProvider().layoutCard?.hide()
            return
        }

        if (_firstDisplay) {
            _firstDisplay = false
            val adShown = showAds(showLoading = initializeLoading())
            if (adShown && _hasNativeAds) {
                startSmartRefreshTimer()
            }
        } else {
            if (_hasNativeAds) {
                if (shouldRefreshAds()) {
                    refreshNative()
                }
                startSmartRefreshTimer()
            }
        }
    }
    override fun onPause(owner: LifecycleOwner) {
        cancelRefreshTimer()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cancelRefreshTimer()
        if (_requestNativeId.isNotBlank()) {
            CoreAds.instance.releaseNativeAds(_requestNativeId)
            _requestNativeId = ""
        }
        viewContainerProvider().clear()
        owner.lifecycle.removeObserver(this)
    }

    fun onNetworkStateChanged(isConnected: Boolean) {
        if (isConnected && isAtLeastMinActiveState()) {
            val adShown = showAds(showLoading = false)
            if (adShown && _hasNativeAds) {
                startSmartRefreshTimer()
            }
        }
    }

    override fun showAds(showLoading: Boolean): Boolean {
        val container = viewContainerProvider().adsContainer ?: return false
        val card = viewContainerProvider().layoutCard ?: return false
        val actv = activityProvider() ?: return false

        if (CoreAds.instance.isHideAds) {
            card.hide()
            _hasNativeAds = false
            _hasBannerAds = false
            cancelRefreshTimer()
            return false
        }

        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            card.hide()
            _hasNativeAds = false
            _hasBannerAds = false
            cancelRefreshTimer()
            return false
        }

        val tag = screenTagProvider()
        val tagNative = "${tag}_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull { it.tag == tagNative && !it.id.isNullOrBlank() }
        if (nativeAds != null) {
            Timber.tag(tagNative).i( "Show ads...")
            card.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            if (tag.contains("NativeFullscreen")) {
                card.setMargins(left = 0, right = 0)
                card.radius = 0f
            } else {
                card.setMargins(left = 16.px, right = 16.px)
                card.radius = 10.px.toFloat()
            }
            val nHeight = nativeHeightProvider()
            if (nHeight >= 0) {
                val height = actv.resources.displayMetrics.heightPixels
                val maxH = if (nHeight == 0) min((height - 24.px) / 3, 350.px) else nHeight
                container.viewTreeObserver.addOnGlobalLayoutListener(
                    OnViewGlobalLayoutListener(
                        container,
                        maxH
                    )
                )
            }
            _hasNativeAds = true
            _hasBannerAds = false
            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                container,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        if (isContextValid() && isAtLeastMinActiveState()) {
                            viewContainerProvider().adsContainer?.let { safeContainer ->
                                val retAds = CoreAds.instance.showAdmobNativeAds(safeContainer, nativeAds.style ?: NativeStyle.BIG_10)
                                _timeStamp = System.currentTimeMillis()
                                retAds?.let { _requestNativeId = it.requestId }
                            }
                        }
                    }
                }
            )
            if (aNative != null) {
                _timeStamp = System.currentTimeMillis()
                _requestNativeId = aNative.requestId
            } else if (showLoading) {
                onShowLoading?.invoke(true)
            }
            return true
        }

        val tagBanner = "${tag}_Banner"
        val bannerAds = remoteConfig.banners?.firstOrNull { it.tag == tagBanner && !it.id.isNullOrBlank() }
        if (bannerAds != null) {
            cancelRefreshTimer()
            Timber.tag(tagBanner).i( "Show ads...")
            val size = when (bannerAds.size) {
                "medium" -> {
                    card.layoutParams.width = 300.px
                    card.radius = 10.px.toFloat()
                    AdSize.MEDIUM_RECTANGLE
                }
                "full" -> {
                    card.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    card.setMargins(0, 0)
                    card.radius = 0f
                    AdSize.FULL_BANNER
                }
                "inline" -> {
                    card.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    card.setMargins(16.px, 16.px)
                    card.radius = 10.px.toFloat()
                    actv.calculateBannerHeightBy()
                }
                else -> {
                    card.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    card.setMargins(0, 0)
                    card.radius = 0f
                    null
                }
            }
            val banner = CoreAds.instance.showAdapterBannerAds(
                actv,
                container,
                bannerAds.id!!,
                bannerAds.event ?: tagBanner,
                size,
                if (_firstTimeShownBanner) bannerAds.collapsible_type else null,
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        if (isContextValid() && isAtLeastMinActiveState()) {
                            viewContainerProvider().adsContainer?.let { safeContainer ->
                                CoreAds.instance.showAvailableBanner(safeContainer, bannerAds.id!!, bannerAds.event ?: tagBanner, size)
                            }
                        }
                    }
                }
            )
            _firstTimeShownBanner = false
            _hasNativeAds = false
            _hasBannerAds = true
            if (banner == null && showLoading) {
                onShowLoading?.invoke(true)
            }
            return true
        }

        _hasBannerAds = false
        _hasNativeAds = false
        cancelRefreshTimer()
        card.hide()
        return false
    }

    override fun refreshNative() {
        val container = viewContainerProvider().adsContainer ?: return
        val card = viewContainerProvider().layoutCard ?: return
        if (CoreAds.instance.isHideAds) {
            _hasNativeAds = false
            cancelRefreshTimer()
            card.hide()
            return
        }
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: return
        val tagNative = "${screenTagProvider()}_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull { it.tag == tagNative && !it.id.isNullOrBlank() } ?: return
        Timber.tag("AdsDelegate").i("Refreshing Native ad (30s) for ${screenTagProvider()}")
        val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
            container,
            nativeAds.id!!,
            nativeAds.event ?: tagNative,
            nativeAds.style ?: NativeStyle.BIG_10,
            object : LoadCallback() {
                override fun onLoadSuccess() {
                    if (isContextValid() && isAtLeastMinActiveState()) {
                        viewContainerProvider().adsContainer?.let { safeContainer ->
                            val retAds = CoreAds.instance.showAdmobNativeAds(safeContainer, nativeAds.style ?: NativeStyle.BIG_10)
                            _timeStamp = System.currentTimeMillis()
                            retAds?.let { _requestNativeId = it.requestId }
                        }
                    }
                }
            }
        )
        if (aNative != null) {
            _timeStamp = System.currentTimeMillis()
            _requestNativeId = aNative.requestId
        }
    }

    override fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?) {
        val actv = activityProvider()
        if (actv == null || actv.isFinishing || actv.isDestroyed || CoreAds.instance.isHideAds) {
            onCompleted?.invoke()
            return
        }
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull { it.tag == tag }
        if (ads?.id.isNullOrBlank()) {
            onCompleted?.invoke()
            return
        }
        var nativeId: String? = null
        val isTriggered = CoreAds.instance.showAdapterInterstitialAds(
            ads.timelapse ?: 0,
            actv.getString(StringResId.loadingAds),
            actv,
            ads.id!!,
            ads.event ?: "ClickInterDummy",
            object : AdsCallback() {
                override fun onClosed() {
                    super.onClosed()
                    if (nativeId.isNullOrBlank() || viewContainerProvider().nativeFullContainer == null) {
                        safeExecuteAction(onCompleted)
                    }
                }
                override fun onError(message: String?) {
                    super.onError(message)
                    _nativeFullId = ""
                    safeExecuteAction(onCompleted)
                }
                override fun onShow() {
                    if (!nativeId.isNullOrBlank() && viewContainerProvider().nativeFullContainer != null) {
                        _nativeFullId = nativeId!!
                        showNativeFull(tag)
                    }
                }
            }
        )
        if (isTriggered) {
            nativeId = ads.nativeId
        }
    }

    override fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?) {
        val actv = activityProvider() ?: run { onCompleted?.invoke(); return }
        val tagBack = "${screenTagProvider()}_Back"
        val tagNext = "${screenTagProvider()}_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull { it.tag == tagBack || it.tag == tagNext }
        if (ads?.id.isNullOrBlank()) {
            onCompleted?.invoke()
            return
        }
        CoreAds.instance.showAdapterInterstitialAds(
            ads.timelapse ?: 0,
            actv.getString(StringResId.loadingAds),
            actv,
            ads.id!!,
            ads.event ?: "DummyEvent",
            object : AdsCallback() {
                override fun onClosed() {
                    super.onClosed()

                    safeExecuteAction(onCompleted)
                }

                override fun onError(message: String?) {
                    super.onError(message)

                    safeExecuteAction(onCompleted)
                }

                override fun onShow() {
                    Timber.tag("MONET-DEBUG").i("Fragment Inter shown!")
                    safeExecuteAction(onShown)
                }
            }
        )
    }

    override fun preloadAds() {
        val actv = activityProvider() ?: return
        val tag = screenTagProvider()
        val tagBack = tag + "_Back"
        val tagNext = tag + "_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: return
        val ads = rmConfig.interstitials?.firstOrNull {
            it.tag == tagBack || it.tag == tagNext
        }
        if (ads != null && !ads.id.isNullOrBlank()) {
            Timber.tag("MONET-DEBUG").d("Preload inter ads in create fragment!!!")
            CoreAds.instance.initAdapterInterstitialAds(actv, ads.id!!, ads.event ?: "")
        }

        val nativeAds = rmConfig.natives?.firstOrNull {
            it.place_preload == tag
        }
        if (nativeAds != null && !nativeAds.id.isNullOrBlank()) {
            Timber.tag("MONET-DEBUG").d("Preload Native ads in create fragment!!!")
            CoreAds.instance.preloadAdmobNativeAds(nativeAds.id!!, nativeAds.event ?: "DUMMY")
            CoreAds.instance.loadOrShowAdmobNativeAds(
                null,
                nativeAds.id!!,
                nativeAds.event ?: "DummyEventNative",
                nativeAds.style ?: NativeStyle.BIG_13
            )
        }
    }

    private fun showNativeFull(tag: String) {
        val container = viewContainerProvider().nativeFullContainer ?: return
        container.show()
        viewContainerProvider().closeNativeFullAds?.show()
        CoreAds.instance.loadOrShowAdmobNativeAds(
            container,
            _nativeFullId,
            "${tag}NativeFull",
            NativeStyle.FULLSCREEN,
            object : LoadCallback() {
                override fun onLoadSuccess() {
                    if (isContextValid()) {
                        viewContainerProvider().nativeFullContainer?.let { safeContainer ->
                            val retAds = CoreAds.instance.showAdmobNativeAds(safeContainer, NativeStyle.FULLSCREEN)
                            retAds?.let { _requestNativeId = it.requestId }
                        }
                    }
                }
            }
        )
        _nativeFullId = ""
    }

    private fun shouldRefreshAds(): Boolean {
        return (System.currentTimeMillis() - _timeStamp) >= REFRESH_INTERVAL_MS
    }

    private fun startSmartRefreshTimer() {
        val owner = lifecycleOwnerProvider() ?: return
        cancelRefreshTimer()
        _refreshJob = owner.lifecycleScope.launch {
            while (isActive && _hasNativeAds && !CoreAds.instance.isHideAds) {
                val timeElapsed = System.currentTimeMillis() - _timeStamp
                val remainingTime = (REFRESH_INTERVAL_MS - timeElapsed).coerceAtLeast(1000L)
                delay(remainingTime)
                if (isActive && isAtLeastMinActiveState() && isContextValid()) {
                    refreshNative()
                }
            }
        }
    }
    private fun cancelRefreshTimer() {
        _refreshJob?.cancel()
        _refreshJob = null
    }

    private fun isContextValid(): Boolean {
        val actv = activityProvider() ?: return false
        return !actv.isFinishing && !actv.isDestroyed
    }

    private fun isAtLeastMinActiveState(): Boolean {
        val owner = lifecycleOwnerProvider() ?: return false
        return owner.lifecycle.currentState.isAtLeast(minActiveState)
    }

    private fun safeExecuteAction(action: (() -> Unit)?) {
        if (isContextValid()) {
            action?.invoke()
        }
    }
}