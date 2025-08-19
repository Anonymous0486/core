package org.app.core.base.extensions

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.databinding.BindingAdapter
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import org.app.core.base.BaseAdapter
import org.app.core.base.utils.SafeClickListener
import org.app.core.R
import org.app.core.base.utils.px
import java.io.File
import java.lang.reflect.ParameterizedType
import androidx.core.graphics.toColorInt
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isInvisible
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors

fun View.show() {
    if (isVisible) return

    visibility = View.VISIBLE
    if (this is Group) {
        this.requestLayout()
    }
}

fun View.setMargins(
    left: Int = this.marginLeft,
    top: Int = this.marginTop,
    right: Int = this.marginRight,
    bottom: Int = this.marginBottom,
) {
    layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
        setMargins(left, top, right, bottom)
    }
}

fun View.show(isShow: Boolean) {
    if (isShow) {
        show()
    } else {
        hide()
    }
    if (this is Group) {
        this.requestLayout()
    }
}

fun View.hide() {
    if (isGone) return

    visibility = View.GONE
    if (this is Group) {
        this.requestLayout()
    }
}

fun View.invisible() {
    if (isInvisible) return

    visibility = View.INVISIBLE
    if (this is Group) {
        this.requestLayout()
    }
}

@BindingAdapter("app:goneUnless")
fun View.goneUnless(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
    if (this is Group) {
        this.requestLayout()
    }
}

@BindingAdapter(
    value = ["app:bgColor", "app:roundRadius", "app:rippleColor", "app:disabledColor"],
    requireAll = false
)
fun View.setCustomBackground(
    bgColor: String? = null,
    roundRadius: Int? = null,
    rippleColor: String? = null,
    disabledColor: String? = null
) {
    val colorControl = MaterialColors.getColor(context, android.R.attr.colorControlHighlight, "#DDDDDD".toColorInt())  // System lighter gray color - #DDDDDD
    val states = arrayOf(
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_enabled),
        intArrayOf(-android.R.attr.state_enabled)
    )
    val colors = intArrayOf(
        if(rippleColor.isNullOrBlank()) colorControl else rippleColor.toColorInt(),
        (bgColor ?: "#FFFFFF").toColorInt(),
        (disabledColor ?: "#DDDDDD").toColorInt()
    )
    val shapeDrawable = MaterialShapeDrawable()
    shapeDrawable.fillColor = ColorStateList(states, colors)

    if (roundRadius != null &&  roundRadius > 0) {
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel.toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, roundRadius.px.toFloat())
            .build()
    }
    background = shapeDrawable
}

fun ImageView.drawCircle(backgroundColor: String, borderColor: String? = null) {
    try {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    
        shape.setColor(Color.parseColor(backgroundColor))
    
        borderColor?.let {
            shape.setStroke(10, Color.parseColor(it))
        }
    
        background = shape
    } catch (_: Exception) { }
}

fun <VB : ViewBinding> Any.inflateViewBinding(
    inflater: LayoutInflater,
    parent: ViewGroup? = null,
    attachToParent: Boolean = false
): VB {
    val clazz =
        (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<VB>
    return clazz.getMethod(
        "inflate",
        LayoutInflater::class.java,
        ViewGroup::class.java,
        Boolean::class.java
    ).invoke(null, inflater, parent, attachToParent) as VB
}

fun ImageView.setTintR(@ColorRes id: Int) =
    setColorFilter(ContextCompat.getColor(context, id), PorterDuff.Mode.SRC_IN)

fun ImageView.setTint(color: Int) =
    setColorFilter(color, PorterDuff.Mode.SRC_IN)

fun View.enable() {
    isEnabled = true
    alpha = 1f
}

fun ViewPager2.reduceDragSensitivity() {
    val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
    recyclerViewField.isAccessible = true
    val recyclerView = recyclerViewField.get(this) as RecyclerView

    val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
    touchSlopField.isAccessible = true
    val touchSlop = touchSlopField.get(recyclerView) as Int
    touchSlopField.set(recyclerView, touchSlop * 8)       // "8" was obtained experimentally
}

fun View.disable() {
    isEnabled = false
    alpha = 0.3f
}

fun View.showSnackBar(
    message: String,
    retryActionName: String? = null,
    action: (() -> Unit)? = null
) {
    val snackBar = Snackbar.make(this, message, Snackbar.LENGTH_LONG)

    action?.let {
        snackBar.setAction(retryActionName) {
            it()
        }
    }

    snackBar.show()
}

@BindingAdapter(value = ["app:loadImage", "app:progressBar"], requireAll = false)
fun ImageView.loadImage(imageUrl: String?, progressBar: ProgressBar?) {
    if (!imageUrl.isNullOrEmpty()) {
        val circularProgressDrawable = CircularProgressDrawable(context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.setColorSchemeColors(R.color.color_primary)
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()
        Glide.with(context)
            .load(imageUrl)
            .apply(
                RequestOptions()
                    .error(R.drawable.img_place_holder)
                    .placeholder(circularProgressDrawable)
            )
            .into(this)
    } else {
        setImageResource(R.drawable.img_place_holder)
    }
}

@BindingAdapter(value = ["app:loadCircleImage", "app:progressBar"], requireAll = false)
fun ImageView.loadCircleImage(imageUrl: String?, progressBar: ProgressBar?) {
    if (!imageUrl.isNullOrEmpty()) {
        val placeholder = BitmapFactory.decodeResource(
            resources,
            R.drawable.img_place_holder
        )
        val circularBitmapDrawable = RoundedBitmapDrawableFactory.create(resources, placeholder)
        circularBitmapDrawable.isCircular = true
        Glide.with(this.context)
            .load(imageUrl)
            .apply(
                RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.ALL)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .error(circularBitmapDrawable)
                    .placeholder(circularBitmapDrawable).circleCrop().dontAnimate()
            )
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(this)
    }
}

@BindingAdapter(value = ["app:loadRoundImage", "app:progressBar"], requireAll = false)
fun ImageView.loadRoundImage(imageUrl: String?, progressBar: ProgressBar?) {
    if (!imageUrl.isNullOrEmpty()) {
        Glide.with(context)
            .load(imageUrl)
            .transform(RoundedCorners(resources.getDimension(R.dimen.dimen7).toInt()))
            .placeholder(R.drawable.img_place_holder)
            .error(R.drawable.img_place_holder)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(this)
    }
}

@BindingAdapter("load_drawable")
fun loadDrawable(imageView: ImageView, drawable: Drawable?) {
    imageView.setImageDrawable(drawable)
}

@BindingAdapter("imgRes")
fun loadResourceIcon(imgView: ImageView, resId: Int) {
    if (resId > 0) Glide.with(imgView.context).load(resId).into(imgView)
}

fun View.setSafeOnClickListener(onSafeClick: (View?) -> Unit) {
    val safeClickListener = SafeClickListener {
        onSafeClick(it)
    }
    setOnClickListener(safeClickListener)
}

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

fun View.doOnViewDrawnRepeat(removeCallbackBy: (() -> Boolean) = { true }, onDrawn: () -> Unit) {
    var global: ViewTreeObserver.OnGlobalLayoutListener? = null
    global = ViewTreeObserver.OnGlobalLayoutListener {
        onDrawn()
        if (removeCallbackBy()) {
            viewTreeObserver.removeOnGlobalLayoutListener(global)
        }
    }
    viewTreeObserver.addOnGlobalLayoutListener(global)
}

@BindingAdapter("imageUri")
fun setImageUri(imgView: ImageView, imgUri: Uri?) {
    imgUri?.let {
        Glide.with(imgView.context)
            .load(it)
            .centerCrop()
            .apply(
                RequestOptions()
                    .error(R.drawable.img_place_holder)
            )
            .into(imgView)
    }
}

@BindingAdapter("imageUrl")
fun setImageUrl(imgView: ImageView, imgUrl: String?) {
    imgUrl?.let {
        val circularProgressDrawable = CircularProgressDrawable(imgView.context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.setColorSchemeColors(R.color.colorPrimary)
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()
        Glide.with(imgView.context)
            .load(imgUrl)
            .apply(
                RequestOptions()
                    .error(R.drawable.img_place_holder)
                    .placeholder(circularProgressDrawable)
            )
            .into(imgView)
    }
}

fun <D, VB : ViewDataBinding, A : BaseAdapter<D, VB>>
        RecyclerView.setupVertical(recyclerViewAdapter: A) {
    apply {
        layoutManager = LinearLayoutManager(context)
        adapter = recyclerViewAdapter
        isNestedScrollingEnabled = false
    }
}

fun <D, VB : ViewDataBinding, A : BaseAdapter<D, VB>>
        RecyclerView.setupGrid(recyclerViewAdapter: A, column: Int) {
    apply {
        layoutManager = GridLayoutManager(context, column)
        adapter = recyclerViewAdapter
    }
}

fun ImageView.loadImageUri(uri: Uri?) {
    if (uri != null) {
        val circularProgressDrawable = CircularProgressDrawable(context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.setColorSchemeColors(R.color.colorPrimary)
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()
        Glide.with(context)
            .load(uri)
            .apply(
                RequestOptions()
                    .error(R.drawable.img_place_holder)
                    .placeholder(circularProgressDrawable)
            )
            .into(this)
    }
}

@SuppressLint("ClickableViewAccessibility")
fun EditText.onEndDrawableClicked(onClicked: (view: EditText, flag: Boolean) -> Unit) {
    this.setOnTouchListener { v, event ->
        var hasConsumed = false
        if (v is EditText) {
            if (event.action == MotionEvent.ACTION_UP) {
                hasConsumed = true
                if (event.x >= v.width - v.totalPaddingRight) {
                    onClicked(this, true)
                } else {
                    onClicked(this, false)
                }
            }
        }
        hasConsumed
    }
}

@BindingAdapter(value = ["app:localRoundImage", "app:cRadius"], requireAll = false)
fun setLocalImage(imgView: ImageView, image: String?, cornerRadius: Int?) {
    image?.let {
        imgView.loadImageFilePath(image, ImageViewType.SQUARE, cornerRadius?.toFloat() ?: 0f)
    }
}

@BindingAdapter("localCircleImage")
fun setLocalCircleImage(imgView: ImageView, image: String?) {
    if (image != null && image.isNotBlank()) {
        try {
            imgView.loadImageUri(Uri.parse(image), ImageViewType.CIRCLE)
        } catch (ex: Exception) {
            imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
        }
    } else {
        imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
    }
}


@BindingAdapter("localCircleImagePath")
fun setLocalCircleImagePath(imgView: ImageView, image: String?) {
    if (image != null && image.isNotBlank()) {
        try {
            imgView.loadImageFilePath(image, ImageViewType.CIRCLE)
        } catch (ex: Exception) {
            imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
        }
    } else {
        imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
    }
}

@BindingAdapter("image_source", "is_local")
fun setCircleImageBy(imgView: ImageView, source: String?, is_local: Boolean = true){
    if (source != null && source.isNotBlank()) {
        try {
            if (is_local) {
                imgView.loadImageFilePath(source, ImageViewType.CIRCLE)
            } else {
                imgView.loadImageUrl(source, ImageViewType.CIRCLE)
            }
        } catch (ex: Exception) {
            imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
        }
    } else {
        imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.CIRCLE)
    }
}

@BindingAdapter("normal_image_source", "is_local")
fun setNormalImageBy(imgView: ImageView, source: String?, is_local: Boolean = true){
    if (source != null && source.isNotBlank()) {
        try {
            if (is_local) {
                imgView.loadImageFilePath(source, ImageViewType.NONE)
            } else {
                imgView.loadImageUrl(source, ImageViewType.NONE)
            }
        } catch (ex: Exception) {
            imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.NONE)
        }
    } else {
        imgView.loadImageResource(R.drawable.ic_avatar, ImageViewType.NONE)
    }
}

@BindingAdapter("circleImageRes")
fun setCircleImageRes(imgView: ImageView, resId: Int) {
    if (resId != 0) imgView.loadImageResource(resId, ImageViewType.CIRCLE)
}

@BindingAdapter("imageRes")
fun setImageRes(imgView: ImageView, resId: Int) {
    if (resId > 0) {
        val circularProgressDrawable = CircularProgressDrawable(imgView.context)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.setColorSchemeColors(R.color.color_primary)
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()
        Glide.with(imgView.context)
            .load(resId)
            .apply(
                RequestOptions()
                    .error(R.drawable.img_place_holder)
                    .placeholder(circularProgressDrawable)
            )
            .into(imgView)
    }
}

private fun ImageView.glideLoadImage(requestBuilder: RequestBuilder<Drawable>, type: ImageViewType, cornerRadius: Float = 5f) {
    val option = requestBuilder.diskCacheStrategy(DiskCacheStrategy.ALL)
    when (type) {
        ImageViewType.CIRCLE -> {
            option.transition(DrawableTransitionOptions.withCrossFade())
                .transform(CircleCrop())
                .into(this)
        }
        ImageViewType.HRECT, ImageViewType.VRECT, ImageViewType.SQUARE -> {
            val opt = if (cornerRadius > 0) {
                RequestOptions().transform(CenterCrop(), RoundedCorners(dipToPix(cornerRadius, context).toInt()))
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


fun ImageView.loadImageAsset(path: String, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(Uri.parse("file:///android_asset/$path")), type, cornerRadius)
}

fun ImageView.loadImageFile(f: File, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(f), type, cornerRadius)
}

fun ImageView.loadImageFilePath(path: String, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(File(path)), type, cornerRadius)
}

fun ImageView.loadImageResource(resId : Int, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(resId), type, cornerRadius)
}

fun ImageView.loadImageUrl(url: String, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(url), type, cornerRadius)
}

fun ImageView.loadImageUri(url: Uri, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(url), type, cornerRadius)
}

fun ImageView.loadImageBitmap(bm: Bitmap, type: ImageViewType, cornerRadius: Float = 5f) {
    glideLoadImage(Glide.with(this).load(bm), type, cornerRadius)
}

enum class ImageViewType {
    NONE, CIRCLE, HRECT, VRECT, SQUARE
}

@SuppressLint("ClickableViewAccessibility")
fun TextView.onEndDrawableClicked(onClicked: (view: TextView, flag: Boolean) -> Unit) {
    this.setOnTouchListener { v, event ->
        var hasConsumed = false
        if (v is TextView) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                hasConsumed = true
                if (event.x >= v.width - v.totalPaddingRight) {
                    onClicked(this, true)
                } else {
                    onClicked(this, false)
                }
            }
        }
        hasConsumed
    }
}

fun View.takeScreenShot(scaleWidth: Int? = null): Bitmap? {
    try {
        val bmWidth = this.measuredWidth
        val bmHeight = this.measuredHeight
        val screenshot = Bitmap.createBitmap(
            bmWidth,
            bmHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(screenshot)
        this.draw(canvas)

        scaleWidth?.let { width ->
            val sx: Float = width / bmWidth.toFloat()
            val m = Matrix()
            m.setScale(sx, sx)
            return Bitmap.createBitmap(screenshot, 0, 0, width, bmHeight, m, true)
        }
        return screenshot
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return null
}

fun TextView.append(string: String, @ColorRes color: Int, typeface: Typeface? = null) {
    if (string.isEmpty()) {
        return
    }

    val spannable: Spannable = SpannableString(string)
    spannable.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, color)),
        0,
        spannable.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    typeface?.let {
        spannable.setSpan(
            StyleSpan(it.style),
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    append(spannable)
}

fun String.append(txt: String, @ColorInt color: Int, typeface: Typeface? = null) : SpannableStringBuilder {
    val spannableStringBuilder = SpannableStringBuilder(this)
    val spannable: Spannable = SpannableString(txt)
    spannable.setSpan(
        ForegroundColorSpan(color),
        0,
        spannable.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    typeface?.let {
        spannable.setSpan(
            StyleSpan(it.style),
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    spannableStringBuilder.append(spannable)

    return spannableStringBuilder
}

//fun View.fadeoutTo(v: View) {
//    val fadeOut = AnimationUtils.loadAnimation(context, R.anim.fade_out)
//    val fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in)
//
//    fadeOut.setAnimationListener(object : Animation.AnimationListener {
//        override fun onAnimationEnd(animation: Animation?) {
//            visibility = View.GONE
//            v.startAnimation(fadeIn)
//            v.visibility = View.VISIBLE
//        }
//        override fun onAnimationStart(animation: Animation?) {}
//        override fun onAnimationRepeat(animation: Animation?) {}
//    })
//
//    startAnimation(fadeOut)
//}