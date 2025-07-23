package org.app.core.ads.base

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.app.core.databinding.LayoutSmallPlaceHolderBinding
import org.app.core.feature.extension.ImageViewType
import org.app.core.feature.extension.loadImageUrl

class SmallPlaceHolder(
    private val title: String,
    private val subTitle: String,
    private val imageView1: String,
    private val imageView2: String,
    private val desc: String,
    context: Context
) : BaseView<LayoutSmallPlaceHolderBinding>(context) {

    init {
        binding.titleTv.text = title
        binding.subTitleTv.text = subTitle
        binding.iconImg.loadImageUrl(imageView1, ImageViewType.CIRCLE)
        binding.mediaImg.loadImageUrl(imageView1, ImageViewType.SQUARE)
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