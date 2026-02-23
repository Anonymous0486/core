package org.app.core.ads.base

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import org.app.core.R
import org.app.core.ads.callback.AdsCallback
import org.app.core.base.extensions.layoutInflater

abstract class MRECsAds<T> protected constructor(
    context: Context,
    protected var container: FrameLayout?,
    adId: String
) : BaseAds<T>(context = context, adId = adId) {

    init {
        enableShimmer(R.layout.shimmer_mrec)
    }

    override fun show(activity: Activity, callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing()) return

        if (!isAvailable) load()
        showAds(activity)
    }

    protected var shimmer: ShimmerFrameLayout? = null
    private fun enableShimmer(shimmerLayoutId: Int) {
        val shimmerBuilder = Shimmer.ColorHighlightBuilder()
            .setBaseColor(ContextCompat.getColor(context, R.color.shimmer_base_color))
            .setHighlightColor(ContextCompat.getColor(context, R.color.shimmer_highlight_color))
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        shimmer = ShimmerFrameLayout(context)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER
        shimmer!!.layoutParams = layoutParams

        context.layoutInflater.inflate(shimmerLayoutId, shimmer)
        shimmer!!.setShimmer(shimmerBuilder.build())
        shimmer!!.startShimmer()

        container?.removeAllViews()
        container?.addView(shimmer)
    }
}