package org.app.core.ads.nativeads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.nativead.NativeAd
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.BaseAds
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.ads.remoteconfig.config.BackupAds
import org.app.core.ads.remoteconfig.type.BackupType
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

data class AdmobNativeAds(
    val requestId: String = UUID.randomUUID().toString(),
    private val context: Context,
    val adUnitId: String,
    val event: String
) {
    private val TAG = "NativeAdmob"
    private var _nativeAd: NativeAd? = null
    private var _displayWhenLoaded: Boolean = true
    var _mixedAdView: AdManagerAdView? = null
    var _isLoading = false
    var _isShown = false

    private var nativeAdLoader: AdLoader? = null
    var backupAds: LinkedBlockingQueue<BackupAds> = LinkedBlockingQueue<BackupAds>()

    private var loadCallback: LoadCallback? = null

    fun loadAds() {
        logEvent("Request")

        backupAds.clear()
        val rmBackAds = CoreRemoteConfig.instance.getBackupAds(BackupType.NATIVE)
            .sortedBy { it.order }
        if (rmBackAds.isNotEmpty()) {
            backupAds.addAll(rmBackAds)
        }
        Timber.tag(TAG).d("NativeAdmob Start request ads")

        _isLoading = true
        _isShown = false
        setupOptionLoadRequest()
        nativeAdLoader?.loadAd(AdRequest.Builder().build())
    }

    fun isAvailable(): Boolean {
        return (_nativeAd != null || _mixedAdView != null) && !_isShown
    }

    fun isLoading(): Boolean {
        return _isLoading
    }

    fun isDisplayed(): Boolean {
        if (_isShown) {
            destroyAds()
        }
        return _isShown
    }

    fun setDisplayWhenLoaded(flag: Boolean) {
        _displayWhenLoaded = flag
    }

    fun setLoadCallback(callback: LoadCallback?) {
        loadCallback = callback
    }

    fun destroyAds() {
        _nativeAd?.destroy()
        _nativeAd = null
        _mixedAdView?.destroy()
        _mixedAdView = null
        nativeAdLoader = null
        loadCallback = null
        Timber.tag(TAG).d("destroyAds")
    }

    private fun setupOptionLoadRequest() {
        // Need Check more about option is needed?
//        val videoOptions =
//            VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true).build()
//        val nativeAdOptions: NativeAdOptions = Builder()
//            .setMediaAspectRatio(MediaAspectRatio.ANY)
//            .setVideoOptions(videoOptions)
//            .build()

        val builder = AdLoader.Builder(context, adUnitId).forNativeAd { nativeAd: NativeAd ->
            nativeAd.setOnPaidEventListener { adValue ->
                Timber.tag(TAG).d("onPaidEvent $adUnitId : ${adValue.valueMicros}")
                logEvent("Paid")
            }

            _nativeAd = nativeAd
            _mixedAdView?.destroy()
            _mixedAdView = null
        }.forAdManagerAdView({ adView ->
            _mixedAdView = adView
            _nativeAd?.destroy()
            _nativeAd = null

        }, AdSize.BANNER, AdSize.FULL_BANNER)

        nativeAdLoader = builder
            .withAdListener(object : AdListener() {
                override fun onAdOpened() {
                    super.onAdOpened()
                    Timber.tag(TAG).d("onAdOpened")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    Timber.tag(TAG).d("NativeAdmob onAdFailedToLoad: ${loadAdError.code} -> ${loadAdError.message} -> $adUnitId")

                    if (backupAds.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    } else {
                        _mixedAdView?.destroy()
                        _mixedAdView = null
                        _nativeAd?.destroy()
                        _nativeAd = null
                        _isLoading = false
                    }
                    logEvent("LoadFail_${loadAdError.code}")
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Timber.tag(TAG).d("onAdLoaded  -> $adUnitId and ${_nativeAd != null}")
                    _isLoading = false
                    logEvent("Loaded")
                    loadCallback?.onLoadSuccess()
                    if (_displayWhenLoaded) {
                        CoreAds.instance.updateTimestamp(System.currentTimeMillis())
                    }
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    Timber.tag(TAG).d("onAdClosed  -> $adUnitId")
                }

                override fun onAdImpression() {
                    super.onAdImpression()

                    Timber.tag(TAG).d("onAdImpression  -> $adUnitId")
                    _isShown = true
                    logEvent("Impression")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    Timber.tag(TAG).d("onAdClicked  -> $adUnitId")
                    logEvent("Clicked")
                }
            })
//            .withNativeAdOptions(nativeAdOptions)
            .build()
    }

    private fun loadBackupAds() {
        val backupId = backupAds.poll()?.id ?: ""

        if (backupId.isBlank()) {
            Timber.tag(TAG).d("loadBackupAds but empty")
            logEvent("RequestBackupIdButEmpty")
            return
        }

        val builder = AdLoader.Builder(context, backupId)
            .forNativeAd { nativeAd: NativeAd ->
                nativeAd.setOnPaidEventListener { adValue ->
                    Timber.tag(TAG).d("onPaidEvent backupId $backupId : ${adValue.valueMicros}")
                    logEvent("PaidBackupId")
                }

                _nativeAd = nativeAd
                _mixedAdView?.destroy()
                _mixedAdView = null
            }.forAdManagerAdView({ adView ->
                _mixedAdView = adView
                _nativeAd?.destroy()
                _nativeAd = null

            }, AdSize.BANNER, AdSize.FULL_BANNER)

        nativeAdLoader = builder
            .withAdListener(object : AdListener() {
                override fun onAdOpened() {
                    super.onAdOpened()
                    Timber.tag(TAG).d("onAdOpened backup  -> $backupId")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    Timber.tag(TAG).d("onAdFailedToLoad backup  -> ${loadAdError.code} -> ${loadAdError.message} -> $backupId")
                    logEvent("LoadFailBackupId_${loadAdError.code}")
                    if (backupAds.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadBackupAds()
                        }, 1500)
                    } else {
                        _mixedAdView?.destroy()
                        _mixedAdView = null
                        _nativeAd?.destroy()
                        _nativeAd = null
                        _isLoading = false
                    }
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Timber.tag(TAG).d("onAdLoaded backup -> $backupId")
                    _isLoading = false
                    logEvent("LoadedBackupId")
                    loadCallback?.onLoadSuccess()
                    if (_displayWhenLoaded) {
                        CoreAds.instance.updateTimestamp(System.currentTimeMillis())
                    }
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    Timber.tag(TAG).d("onAdClosed backup -> $backupId")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    Timber.tag(TAG).d("onAdImpression backup -> $backupId")

                    _isShown = true
                    logEvent("ImpressionBackupId")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    Timber.tag(TAG).d("onAdClicked backup -> $backupId")
                    logEvent("ClickedBackupId")
                }
            })
            .build()

        logEvent("RequestBackupId")
        _isShown = false
        _isLoading = true
        nativeAdLoader?.loadAd(AdRequest.Builder().build())
    }

    fun showAdView(
        @LayoutRes layoutAdId: Int,
        context: Context,
        adsContainer: FrameLayout,
    ) {
        try {
            if (_mixedAdView != null) {
                Timber.tag("NativeAdmob").d("Show Mixed")
                if (_mixedAdView?.parent != null) {
                    (_mixedAdView?.parent as FrameLayout?)?.removeView(_mixedAdView)
                }
                adsContainer.removeAllViews()
                adsContainer.addView(_mixedAdView)
            } else {
                val dynamicBinding = NativeDisplayViewBinding.inflate(LayoutInflater.from(context), layoutAdId)
                val compatibleBinding = dynamicBinding.toDisplayView()
                populateNativeAdView(_nativeAd?: return, compatibleBinding, layoutAdId)
                adsContainer.removeAllViews()
                adsContainer.addView(compatibleBinding.root ?: return)
                Timber.tag("NativeAdmob").d("Show native")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun populateNativeAdView(nativeAd: NativeAd, compatibleBinding: NativeDisplayView, @LayoutRes layoutAdId: Int,) {
        val nativeAdView = compatibleBinding.root ?: return

        nativeAdView.mediaView = compatibleBinding.adMedia

        nativeAdView.headlineView = compatibleBinding.adHeadline
        nativeAdView.bodyView = compatibleBinding.adBody
        nativeAdView.callToActionView = compatibleBinding.adCallToAction
        nativeAdView.iconView = compatibleBinding.adAppIcon
        nativeAdView.starRatingView = compatibleBinding.adStars
        nativeAdView.storeView = compatibleBinding.adStore
        nativeAdView.advertiserView = compatibleBinding.adAdvertiser

        compatibleBinding.adHeadline?.text = nativeAd.headline
        nativeAd.mediaContent?.let { compatibleBinding.adMedia?.mediaContent = it }
        val scaleType = if (layoutAdId == R.layout.ads_native_full) {
            ImageView.ScaleType.CENTER_INSIDE
        } else {
            ImageView.ScaleType.CENTER_CROP
        }
        compatibleBinding.adMedia?.setImageScaleType(scaleType)

        if (nativeAd.body == null) {
            compatibleBinding.adBody?.visibility = View.GONE
        } else {
            compatibleBinding.adBody?.visibility = View.VISIBLE
            compatibleBinding.adBody?.text = nativeAd.body
        }

        try {
            if (nativeAd.callToAction == null) {
                compatibleBinding.adCallToAction?.visibility = View.GONE
            } else {
                compatibleBinding.adCallToAction?.visibility = View.VISIBLE
                compatibleBinding.adCallToAction?.text = nativeAd.callToAction
                val bg = CoreAds.instance.ctaBackgroundDrawable
                bg?.let {
                    compatibleBinding.adCallToAction?.background = AppCompatResources.getDrawable(context, bg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (nativeAd.icon == null) {
            compatibleBinding.adAppIcon?.visibility = View.GONE
        } else {
            compatibleBinding.adAppIcon?.setImageDrawable(nativeAd.icon?.drawable)
            compatibleBinding.adAppIcon?.visibility = View.VISIBLE
        }

        if (nativeAd.store == null) {
            compatibleBinding.adStore?.visibility = View.GONE
        } else {
            compatibleBinding.adStore?.visibility = View.VISIBLE
            compatibleBinding.adStore?.text = nativeAd.store
        }

        if (nativeAd.starRating == null) {
            compatibleBinding.adStars?.visibility = View.GONE
        } else {
            compatibleBinding.adStars?.rating = nativeAd.starRating!!.toFloat()
            compatibleBinding.adStars?.visibility = View.VISIBLE
        }

        if (nativeAd.advertiser == null) {
            compatibleBinding.adAdvertiser?.visibility = View.GONE
        } else {
            compatibleBinding.adAdvertiser?.text = nativeAd.advertiser
            compatibleBinding.adAdvertiser?.visibility = View.VISIBLE
        }

        nativeAdView.setNativeAd(nativeAd)
    }

    fun createShimmer(context: Context, layoutAdId: Int): ShimmerFrameLayout {
        val shimmerBuilder = Shimmer.AlphaHighlightBuilder()
            .setClipToChildren(true)
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        val shimmer = ShimmerFrameLayout(context)
        shimmer.id = View.generateViewId()
        shimmer.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        val view = LayoutInflater.from(context).inflate(layoutAdId, shimmer)
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

    private fun logEvent(type: String) {
        CoreAds.instance.logFirebaseEvent(event + "_$type")
    }
}
