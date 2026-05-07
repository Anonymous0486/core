package org.app.core.base.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.app.core.base.utils.showMessage
import org.app.core.R
import androidx.core.net.toUri

fun <A : Activity> Activity.openActivityAndClearStack(activity: Class<A>) {
    Intent(this, activity).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(this)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}

fun <A : Activity> Activity.openActivity(activity: Class<A>) {
    Intent(this, activity).apply {
        startActivity(this)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }
}

fun Activity.showMessage(message: String?) {
    Handler(Looper.getMainLooper()).post {
        showMessage(this, message)
    }
}

fun Activity.redirectToPlayStore(appId: String) {
    try {
        this.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$appId".toUri()
            )
        )
    } catch (_: Exception) {}
}