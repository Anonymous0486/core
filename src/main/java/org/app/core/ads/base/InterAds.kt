package org.app.core.ads.base

import android.app.Activity
import org.app.core.ads.dialog.DialogAdsLoading

abstract class InterAds<T> protected constructor(
    activity: Activity,
    adId: String
) : BaseAds<T>(activity = activity, adId = adId) {

    override fun onShowSuccess() {
        super.onShowSuccess()
//        if (dialogLoading == null) {
//            dialogLoading = DialogAdsLoading(activity.applicationContext)
//        }
//        try {
//            dialogLoading!!.show()
//        } catch (_: Exception) {}
    }

    override fun onClosed() {
        super.onClosed()
        if (dialogLoading != null) {
            dialogLoading!!.cancel()
            dialogLoading = null
        }
    }
    
    var showWhenLoaded: Boolean = false
    private var dialogLoading: DialogAdsLoading? = null
}