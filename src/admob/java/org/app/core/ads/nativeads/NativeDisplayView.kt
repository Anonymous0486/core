package org.app.core.ads.nativeads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import org.app.core.R

interface INativeDisplayViewBinding {
    val root: NativeAdView?
    val adMedia: MediaView?
    val adHeadline: TextView?
    val adBody: TextView?
    val adCallToAction: Button?
    val adAppIcon: ImageView?
    val adStars: RatingBar?
    val adStore: TextView?
    val adAdvertiser: TextView?
}


class NativeDisplayViewBinding private constructor(
    override val root: NativeAdView?,
    override val adMedia: MediaView?,
    override val adHeadline: TextView?,
    override val adBody: TextView?,
    override val adCallToAction: Button?,
    override val adAppIcon: ImageView?,
    override val adStars: RatingBar?,
    override val adStore: TextView?,
    override val adAdvertiser: TextView?
) : INativeDisplayViewBinding {

    companion object {
        fun inflate(inflater: LayoutInflater, @LayoutRes layoutId: Int): NativeDisplayViewBinding {
            val rootView = inflater.inflate(layoutId, null, false) as? NativeAdView ?: return dummyView()

            return NativeDisplayViewBinding(
                root = rootView,
                adMedia = findRequiredView(rootView, R.id.ad_media),
                adHeadline = findRequiredView(rootView, R.id.ad_headline),
                adBody = findRequiredView(rootView, R.id.ad_body),
                adCallToAction = findRequiredView(rootView, R.id.ad_call_to_action),
                adAppIcon = findRequiredView(rootView, R.id.ad_app_icon),
                adStars = findRequiredView(rootView, R.id.ad_rating),
                adStore = findRequiredView(rootView, R.id.ad_store),
                adAdvertiser = findRequiredView(rootView, R.id.ad_advertiser)
            )
        }

        private inline fun <reified T : View> findRequiredView(root: View, id: Int): T? {
            val view = root.findViewById(id) as? T
            return view
        }

        private fun dummyView() = NativeDisplayViewBinding(null,null,null,null,null,null,null,null,null)
    }
}

fun NativeDisplayViewBinding.toDisplayView(): NativeDisplayView {
    return NativeDisplayView(this)
}

class NativeDisplayView(private val binding: INativeDisplayViewBinding) {
    val root: NativeAdView? get() = binding.root
    val adMedia: MediaView? get() = binding.adMedia
    val adHeadline: TextView? get() = binding.adHeadline
    val adBody: TextView? get() = binding.adBody
    val adCallToAction: Button? get() = binding.adCallToAction
    val adAppIcon: ImageView? get() = binding.adAppIcon
    val adStars: RatingBar? get() = binding.adStars
    val adStore: TextView? get() = binding.adStore
    val adAdvertiser: TextView? get() = binding.adAdvertiser
}