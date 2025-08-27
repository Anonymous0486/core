package org.app.core.base

import android.view.View
import android.view.ViewTreeObserver


class OnViewGlobalLayoutListener(
    private val view: View,
    private val maxHeight: Int,
) :
    ViewTreeObserver.OnGlobalLayoutListener {

    override fun onGlobalLayout() {
        val params = view.layoutParams
        params.height = maxHeight
        view.layoutParams = params
    }
}