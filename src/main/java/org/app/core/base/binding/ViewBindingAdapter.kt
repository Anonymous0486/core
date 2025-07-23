package org.app.core.base.binding

import android.view.View
import androidx.databinding.BindingAdapter
import org.app.core.base.utils.OnSingleClickListener

@BindingAdapter("isSelected")
fun View.isSelected(isSelected: Boolean) {
    this.isSelected = isSelected
}

@BindingAdapter("isEnabled")
fun View.setEnabled(isEnabled: Boolean) {
    this.isEnabled = isEnabled
    alpha = if (isEnabled) 1f else 0.3f
}

@BindingAdapter("isGone")
fun View.setGone(isGone: Boolean) {
    this.visibility = if (isGone) View.GONE else View.VISIBLE
}

@BindingAdapter("isVisible")
fun View.setVisible(isVisible: Boolean) {
    this.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
}

@BindingAdapter("onSingleClick")
fun View.setOnSingleClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnSingleClickListener(it))
    } ?: setOnClickListener(null)
}

@BindingAdapter("customWidth")
fun View.setLayoutWidth(width: Float) {
    this.layoutParams = this.layoutParams.apply {
        this.width = width.toInt()
    }
}

@BindingAdapter("customHeight")
fun View.setLayoutHeight(height: Float) {
    this.layoutParams = this.layoutParams.apply {
        this.height = height.toInt()
    }
}

@BindingAdapter("backgroundImage")
fun View.setBackgroundImage(resource: Int) {
    this.setBackgroundResource(resource)
}

@BindingAdapter("clipCircle")
fun View.bindClipCircle(clipCircle: Boolean?) {
    outlineProvider = CircleOutlineProvider()
    clipToOutline = true
}

@BindingAdapter("clipRadius", "clipTopLeft", "clipTopRight", "clipBottomLeft", "clipBottomRight", requireAll = false)
fun View.bindClipCorners(radius: Float?, topLeft: Float?, topRight: Float?, bottomLeft: Float?, bottomRight: Float?) {
    this.outlineProvider = RoundOutlineProvider(radius, topLeft, topRight, bottomLeft, bottomRight)
    this.clipToOutline = true
}
