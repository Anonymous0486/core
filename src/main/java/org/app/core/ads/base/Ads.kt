package org.app.core.ads.base

import android.app.Activity
import org.app.core.ads.callback.AdsCallback

abstract class Ads {
    @JvmField
    val TAG = "multiple_mediation"

    /**
     * load ads
     * @return
     */
    abstract fun load(): Ads?

    /**
     * show available ads
     */

    abstract fun show(activity: Activity, callback: AdsCallback? = null)

    /**
     * this callback for ads show successful
     */
    open fun onShowSuccess() {}

    /**
     * this callback for close ads after show
     */
    open fun onClosed() {}

    /**
     * For Native ads only. This callback for granted a reward
     */
    open fun onUserRewarded(amount: Int, type: String) {}

    /**
     * this callback for ads show error (ex: ads null,..)
     */
    open fun onShowError(message: String?) {}

    /**
     * this callback for load ads successful
     */
    open fun onLoadSuccess() {}

    /**
     * this callback for load ads failed
     */
    open fun onLoadFailed(message: String?) {}

    open fun onClick(){}
}