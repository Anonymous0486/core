package org.app.core.ads.base

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdSize
import org.app.core.R
import org.app.core.ads.callback.AdsCallback
import org.app.core.base.extensions.layoutInflater


abstract class BannerAds<T> protected constructor(
    context: Context,
    protected var container: FrameLayout?,
    adId: String,
    adsSize: AdSize? = null,
    isCollapsible: Boolean = false
) : BaseAds<T>(context = context, adId = adId) {

    init {
//        if (container?.childCount == 0) {
//            enableShimmer(adsSize)
//        }
    }

    override fun show(activity: Activity, callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing()) return

        if (!isAvailable) load()
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

        shimmer = ShimmerFrameLayout(context)
        shimmer!!.layoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        context.layoutInflater.inflate(shimmerLayoutId, shimmer)
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

        val shimmer = ShimmerFrameLayout(context)
        shimmer.layoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        context.layoutInflater.inflate(shimmerLayoutId, shimmer)
        shimmer.setShimmer(shimmerBuilder.build())

        container.removeAllViews()
        container.addView(shimmer)
        shimmer.startShimmer()
    }
}