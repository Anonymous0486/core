package org.app.core.ads.base

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.app.core.databinding.LayoutMediumPlaceHolderBinding
import org.app.core.feature.extension.ImageViewType
import org.app.core.feature.extension.loadImageUrl

class MediumPlaceHolder(
    private val title: String,
    private val subTitle: String,
    private val imageView1: String,
    private val imageView2: String,
    private val desc: String,
    context: Context
) : BaseView<LayoutMediumPlaceHolderBinding>(context) {
    init {
        binding.titleTv.text = title
        binding.subTitleTv.text = subTitle
        binding.iconImg.loadImageUrl(imageView1, ImageViewType.SQUARE, 0)
        binding.mediaImg.loadImageUrl(imageView2, ImageViewType.SQUARE)
        binding.root.setOnClickListener {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$desc")
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}