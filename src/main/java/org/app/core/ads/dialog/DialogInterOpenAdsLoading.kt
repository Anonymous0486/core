package org.app.core.ads.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import org.app.core.R

class DialogInterOpenAdsLoading(context: Context): Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_inter_open_ads_loading)

        window!!.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val layoutParams = window!!.attributes
        layoutParams.gravity = Gravity.CENTER
        window!!.attributes = layoutParams
        setCanceledOnTouchOutside(false)
        setCancelable(false)
    }
}