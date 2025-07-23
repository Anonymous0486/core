package org.app.core.feature.extension

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.*

suspend fun <T> runMain(run: suspend () -> T) = withContext(Dispatchers.Main) { run() }

suspend fun <T> runIO(run: suspend CoroutineScope.() -> T) =
    coroutineScope { withContext(Dispatchers.IO) { run() } }

fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit) =
    launch(Dispatchers.IO, block = block)

fun CoroutineScope.launchMain(block: suspend CoroutineScope.() -> Unit) =
    launch(Dispatchers.Main, block = block)

fun coroutinesIO(block:suspend CoroutineScope.()->Unit) = CoroutineScope(Dispatchers.IO).launch {
    block()
}

val Context.layoutInflater: LayoutInflater
    get() = LayoutInflater.from(this)

fun View.doOnViewDrawn(removeCallbackBy: (() -> Boolean) = { true }, onDrawn: () -> Unit) {
    var global: ViewTreeObserver.OnGlobalLayoutListener? = null
    global = ViewTreeObserver.OnGlobalLayoutListener {
        if (removeCallbackBy()) {
            onDrawn()
            viewTreeObserver.removeOnGlobalLayoutListener(global)
        }
    }
    viewTreeObserver.addOnGlobalLayoutListener(global)
}

fun ImageView.loadImageUrl(url: String, type: ImageViewType, cornerRadius: Int = 8) {
    glideLoadImage(Glide.with(this).load(url), type, cornerRadius)
}

val Int.dp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.px: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

private fun ImageView.glideLoadImage(requestBuilder: RequestBuilder<Drawable>, type: ImageViewType, cornerRadius: Int = 8) {
    val option = requestBuilder.diskCacheStrategy(DiskCacheStrategy.ALL)
    when (type) {
        ImageViewType.CIRCLE -> {
            option.transition(DrawableTransitionOptions.withCrossFade())
                .transform(CircleCrop())
                .into(this)
        }
        ImageViewType.HRECT, ImageViewType.VRECT, ImageViewType.SQUARE -> {
            val opt = if (cornerRadius > 0) {
                RequestOptions().transform(CenterCrop(), RoundedCorners(cornerRadius.px))
            } else {
                RequestOptions().transform(CenterCrop())
            }
            option.apply(opt).into(this)
        }
        else -> {
            requestBuilder.diskCacheStrategy(DiskCacheStrategy.ALL).transform(FitCenter()).into(this)
        }
    }
}

enum class ImageViewType {
    NONE, CIRCLE, HRECT, VRECT, SQUARE
}