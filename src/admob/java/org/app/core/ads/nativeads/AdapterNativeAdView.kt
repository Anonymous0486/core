package org.app.core.ads.nativeads

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.utils.convertToCamelCase

class AdapterNativeAdView(
    var isDisplayed: Boolean = false,
    var nativeAd: NativeAd? = null,
    var container: FrameLayout? = null,
    var mixedAdView: AdManagerAdView? = null
) {
    fun showAdView(
        @LayoutRes layoutAdId: Int,
        context: Context,
        adsContainer: FrameLayout,
    ) {
        isDisplayed = true
        try {
            if (mixedAdView != null) {
                adsContainer.removeAllViews()
                adsContainer.addView(mixedAdView)
            } else {
                val adsView = populateNativeAdView(layoutAdId, context, adsContainer)
                adsContainer.removeAllViews()
                adsContainer.addView(adsView)
            }
        } catch (_: Exception) {}
    }

    private fun populateNativeAdView(
        @LayoutRes layoutAdId: Int,
        context: Context,
        adsContainer: FrameLayout,
    ) : NativeAdView {
        val nativeAd = nativeAd ?: return NativeAdView(context)

        isDisplayed = true
        val layoutAd = LayoutInflater.from(context).inflate(layoutAdId, adsContainer, false)

        val adView = NativeAdView(context)
        adView.addView(layoutAd)
        
        val mediaView = adView?.findViewById<MediaView>(R.id.ad_media)
        mediaView?.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        adView?.mediaView = mediaView
        
        // Set other ad assets.
        adView?.headlineView = adView?.findViewById(R.id.ad_headline)
        adView?.bodyView = adView?.findViewById(R.id.ad_body)
        adView?.callToActionView = adView?.findViewById(R.id.ad_call_to_action)
        adView?.iconView = adView?.findViewById(R.id.ad_app_icon)
        adView?.storeView = adView?.findViewById(R.id.ad_advertiser)
        adView?.apply {
            starRatingView = findViewById(resources.getIdentifier("ad_rating", "id", context.packageName))
        }
        
        // The headline is guaranteed to be in every UnifiedNativeAd.
        try {
            (adView?.headlineView as TextView?)?.text = nativeAd.headline
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        try {
            if (nativeAd.body == null) {
                adView?.bodyView?.visibility = View.GONE
            } else {
                adView?.bodyView?.visibility = View.VISIBLE
                (adView?.bodyView as? TextView)?.text = nativeAd.body
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.callToAction == null) {
                adView?.callToActionView?.visibility = View.GONE
            } else {
                adView?.callToActionView?.visibility = View.VISIBLE
                (adView?.callToActionView as? Button)?.text = nativeAd.callToAction!!.split(",")
                    .toTypedArray()[0].convertToCamelCase()
                val bg = CoreAds.instance.ctaBackgroundDrawable
                bg?.let {
                    adView?.callToActionView?.background = AppCompatResources.getDrawable(context, bg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.icon == null) {
                adView?.iconView?.visibility = View.GONE
            } else {
                (adView?.iconView as ImageView?)?.setImageDrawable(
                    nativeAd.icon?.drawable
                )
                adView?.iconView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.price == null) {
                adView?.priceView?.visibility = View.GONE
            } else {
                adView?.priceView?.visibility = View.VISIBLE
                (adView?.priceView as? TextView)?.text = nativeAd.price
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.store == null) {
                adView?.storeView?.visibility = View.GONE
            } else {
                adView?.storeView?.visibility = View.VISIBLE
                (adView?.storeView as? TextView)?.text = nativeAd.store
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.starRating == null) {
                adView?.starRatingView?.visibility = View.GONE
            } else {
                (adView?.starRatingView as? RatingBar)?.rating = nativeAd.starRating?.toFloat() ?: 0f
                adView?.starRatingView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (nativeAd.advertiser == null) {
                adView?.advertiserView?.visibility = View.GONE
            } else {
                (adView?.advertiserView as? TextView)?.text = nativeAd.advertiser
                adView?.advertiserView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad. The SDK will populate the adView's MediaView
        // with the media content from this native ad.
        adView?.setNativeAd(nativeAd)
        
        return adView
    }
}