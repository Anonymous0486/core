package org.app.core.ads.base

import android.app.Activity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.Shimmer.ColorHighlightBuilder
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdSize
import org.app.core.ads.callback.AdsCallback
import org.app.core.R


abstract class BannerAds<T> protected constructor(
    activity: Activity,
    protected var container: FrameLayout?,
    adId: String,
    adsSize: AdSize? = null,
    isCollapsible: Boolean = false
) : BaseAds<T>(activity = activity, adId = adId) {

    init {
        if (container?.childCount == 0) {
            enableShimmer(adsSize)
        }
    }

    override fun show(callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing()) return

        if (!isAvailable) load()
        showAds()
    }

    protected var shimmer: ShimmerFrameLayout? = null
    
    fun enableShimmer(adsSize: AdSize? = null) {
        val shimmerLayoutId = if (adsSize == AdSize.MEDIUM_RECTANGLE) {
            R.layout.layout_normal_ad_placeholder
        } else {
            R.layout.layout_small_ad_placeholder
        }
        val shimmerBuilder = Shimmer.AlphaHighlightBuilder()
            .setClipToChildren(true)
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        shimmer = ShimmerFrameLayout(activity)
        shimmer!!.layoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        activity.layoutInflater.inflate(shimmerLayoutId, shimmer)
        shimmer!!.setShimmer(shimmerBuilder.build())

        container?.removeAllViews()
        container?.addView(shimmer)
        shimmer!!.startShimmer()
    }

    fun showShimmer(adsSize: AdSize? = null, container: FrameLayout) {
        val shimmerLayoutId =  if (adsSize == null) {
            R.layout.layout_small_ad_placeholder
        } else {
            R.layout.layout_normal_ad_placeholder
        }
        val shimmerBuilder = Shimmer.AlphaHighlightBuilder()
            .setClipToChildren(true)
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        val shimmer = ShimmerFrameLayout(activity)
        shimmer.layoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        activity.layoutInflater.inflate(shimmerLayoutId, shimmer)
        shimmer.setShimmer(shimmerBuilder.build())

        container.removeAllViews()
        container.addView(shimmer)
        shimmer.startShimmer()
    }
}