package org.app.core.ads.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.app.core.R


class DialogAdsLoading(context: Context, loadingTxt: String = ""): Dialog(context) {
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ads_loading)

        if (window != null) {
            window!!.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window!!.setBackgroundDrawable(Color.WHITE.toDrawable())
            val layoutParams = window!!.attributes
            layoutParams.gravity = Gravity.CENTER
            window!!.attributes = layoutParams
            WindowCompat.setDecorFitsSystemWindows(window!!, false)
            if (Build.VERSION.SDK_INT >= 30) {
                window!!.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                window!!.setNavigationBarContrastEnforced(false)
                val controller = WindowCompat.getInsetsController(window!!, window!!.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val tv =  findViewById<TextView>(R.id.loadingTv)
        tv.text = loadingTxt
        setCanceledOnTouchOutside(false)
        setCancelable(false)
    }
}