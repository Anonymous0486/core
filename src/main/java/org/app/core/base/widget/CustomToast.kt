package org.app.core.base.widget

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.snackbar.Snackbar
import org.app.core.R

class CustomToast {
    companion object {
        @SuppressLint("InflateParams")
        fun makeText(
            context: Context?,
            contentToast: String,
            @DrawableRes imgRes: Int,
            paddingBottom: Int? = null
        ): Toast {
            val toast = Toast(context)
            val layout =
                LayoutInflater.from(context).inflate(R.layout.custom_toast, null, false)
            val content = layout.findViewById<TextView>(R.id.tvContent)
            val imgToast = layout.findViewById<AppCompatImageView>(R.id.imgToast)

            val padding = paddingBottom ?: (context?.resources?.getDimension(R.dimen.dimen26)?.toInt() ?: 0)
            layout.setPadding(0, 0, 0, padding)
            content?.text = contentToast
            imgToast.setImageResource(imgRes)
            toast.view = layout
            toast.duration = Toast.LENGTH_SHORT
            toast.setGravity(
                Gravity.FILL_HORIZONTAL or Gravity.BOTTOM,
                0,
                0
            )
            return toast
        }
        
        @SuppressLint("InflateParams")
        fun makeText(
            context: Context?,
            @StringRes resContent: Int,
            @DrawableRes imgRes: Int,
            paddingBottom: Int? = null,
            gravity: Int? = null
        ): Toast {
            val toast = Toast(context)
            val layout =
                LayoutInflater.from(context).inflate(R.layout.custom_toast, null, false)
            val content = layout.findViewById<TextView>(R.id.tvContent)
            val imgToast = layout.findViewById<AppCompatImageView>(R.id.imgToast)

            val padding = paddingBottom ?: (context?.resources?.getDimension(R.dimen.dimen16)?.toInt() ?: 0)
            layout.setPadding(0, 0, 0, padding)
            content?.text = context?.getString(resContent)
            imgToast.setImageResource(imgRes)
            toast.view = layout
            toast.duration = Toast.LENGTH_SHORT
            toast.setGravity(
                Gravity.FILL_HORIZONTAL or (gravity ?: Gravity.BOTTOM),
                0,
                0
            )
            return toast
        }
    
        @SuppressLint("InflateParams")
        fun makeSavedToast(
            context: Context,
            view: View,
            text: String,
            onViewAction:() -> Unit,
        ) {
            val snackBar = Snackbar.make(view, "", Snackbar.LENGTH_LONG)
            snackBar.view.setBackgroundResource(R.drawable.bg_round_12)

            val layout = LayoutInflater.from(context).inflate(R.layout.saved_msg_toast, null, false)
            val snackBarLayout = snackBar.view as? Snackbar.SnackbarLayout
            snackBarLayout?.setPadding(0, 0, 0, 0)
            val onActionTv = layout.findViewById<TextView>(R.id.viewAction)
            val content = layout.findViewById<TextView>(R.id.tvContent)
            content?.text = text
    
            onActionTv.setOnClickListener {
                onViewAction.invoke()
                snackBar.dismiss()
            }
            snackBarLayout?.addView(layout, 0)
    
            snackBar.show()
        }
    }
}