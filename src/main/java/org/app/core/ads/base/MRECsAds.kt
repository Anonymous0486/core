package org.app.core.ads.base

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import org.app.core.R
import org.app.core.ads.callback.AdsCallback

abstract class MRECsAds<T> protected constructor(
    activity: Activity,
    protected var container: FrameLayout?,
    adId: String
) : BaseAds<T>(activity = activity, adId = adId) {

    init {
        enableShimmer(R.layout.shimmer_mrec)
    }

    override fun show(callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing()) return

        if (!isAvailable) load()
        showAds()
    }

    protected var shimmer: ShimmerFrameLayout? = null
    private fun enableShimmer(shimmerLayoutId: Int) {
        val shimmerBuilder = Shimmer.ColorHighlightBuilder()
            .setBaseColor(ContextCompat.getColor(activity, R.color.shimmer_base_color))
            .setHighlightColor(ContextCompat.getColor(activity, R.color.shimmer_highlight_color))
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        shimmer = ShimmerFrameLayout(activity)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER
        shimmer!!.layoutParams = layoutParams

        activity.layoutInflater.inflate(shimmerLayoutId, shimmer)
        shimmer!!.setShimmer(shimmerBuilder.build())
        shimmer!!.startShimmer()

        container?.removeAllViews()
        container?.addView(shimmer)
    }
}