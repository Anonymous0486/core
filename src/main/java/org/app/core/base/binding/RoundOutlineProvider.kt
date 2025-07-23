package org.app.core.base.binding

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

class RoundOutlineProvider(
    val radius: Float? = null,
    val topLeft: Float? = null,
    val topRight: Float? = null,
    val bottomLeft: Float? = null,
    val bottomRight: Float? = null,
) : ViewOutlineProvider() {

    private val topCorners = topLeft != null && topLeft == topRight
    private val rightCorners = topRight != null && topRight == bottomRight
    private val bottomCorners = bottomLeft != null && bottomLeft == bottomRight
    private val leftCorners = topLeft != null && topLeft == bottomLeft
    private val topLeftCorner = topLeft != null
    private val topRightCorner = topRight != null
    private val bottomRightCorner = bottomRight != null
    private val bottomLeftCorner = bottomLeft != null

    override fun getOutline(view: View, outline: Outline) {
        val left = 0
        val top = 0
        val right = view.width
        val bottom = view.height

        if (radius != null) {
            val cornerRadius = radius //.typedValue(resources).toFloat()
            outline.setRoundRect(left, top, right, bottom, cornerRadius)
        } else {
            val cornerRadius = topLeft ?: topRight ?: bottomLeft ?: bottomRight ?: 0F

            when {
                topCorners -> outline.setRoundRect(left, top, right, bottom + cornerRadius.toInt(), cornerRadius)
                bottomCorners -> outline.setRoundRect(left, top - cornerRadius.toInt(), right, bottom, cornerRadius)
                leftCorners -> outline.setRoundRect(left, top, right + cornerRadius.toInt(), bottom, cornerRadius)
                rightCorners -> outline.setRoundRect(left - cornerRadius.toInt(), top, right, bottom, cornerRadius)
                topLeftCorner -> outline.setRoundRect(
                    left, top, right + cornerRadius.toInt(), bottom + cornerRadius.toInt(), cornerRadius
                )
                bottomLeftCorner -> outline.setRoundRect(
                    left, top - cornerRadius.toInt(), right + cornerRadius.toInt(), bottom, cornerRadius
                )
                topRightCorner -> outline.setRoundRect(
                    left - cornerRadius.toInt(), top, right, bottom + cornerRadius.toInt(), cornerRadius
                )
                bottomRightCorner -> outline.setRoundRect(
                    left - cornerRadius.toInt(), top - cornerRadius.toInt(), right, bottom, cornerRadius
                )
            }
        }
    }
}

class CircleOutlineProvider : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        val size = view.run { min(width, height) }
        outline.setRoundRect(0, 0, size, size, (size).toFloat())
    }
}
