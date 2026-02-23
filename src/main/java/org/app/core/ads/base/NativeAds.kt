package org.app.core.ads.base

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout
import org.app.core.R
import org.app.core.ads.callback.AdsCallback
import org.app.core.base.extensions.layoutInflater

abstract class NativeAds<T> protected constructor(
    context: Context,
    protected var container: FrameLayout?,
    adId: String
) : BaseAds<T>(context = context, adId = adId) {

    override fun show(activity: Activity, callback: AdsCallback?) {
        setAdsCallback(callback = callback)

        if (isShowing()) return

        if (!isAvailable) load()
    }

    private var shimmer: ShimmerFrameLayout? = null
    fun enableShimmer(shimmerLayoutId: Int) {
        val shimmerBuilder = Shimmer.AlphaHighlightBuilder()
            .setClipToChildren(true)
            .setDuration(1500)
            .setRepeatDelay(500)
            .setHighlightAlpha(0.6f)

        shimmer = ShimmerFrameLayout(context)
        shimmer!!.id = View.generateViewId()
        shimmer!!.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        )
        
        val view = context.layoutInflater.inflate(shimmerLayoutId, shimmer) ?: return
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
        shimmer!!.setShimmer(shimmerBuilder.build())
        shimmer!!.startShimmer()

        container?.removeAllViews()
        container?.addView(shimmer)
    }
}