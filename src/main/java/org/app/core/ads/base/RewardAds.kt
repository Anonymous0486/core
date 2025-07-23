package org.app.core.ads.base

import android.app.Activity
import org.app.core.ads.dialog.DialogAdsLoading

abstract class RewardAds<T> protected constructor(
    activity: Activity,
    adId: String
) : BaseAds<T>(activity = activity, adId = adId) {

    override fun onShowSuccess() {
        super.onShowSuccess()
        if (dialogLoading == null) {
            dialogLoading = DialogAdsLoading(activity)
        }
        dialogLoading!!.show()
    }

    override fun onClosed() {
        super.onClosed()
        if (dialogLoading != null) {
            dialogLoading!!.cancel()
            dialogLoading = null
        }
    }

    private var dialogLoading: DialogAdsLoading? = null
}