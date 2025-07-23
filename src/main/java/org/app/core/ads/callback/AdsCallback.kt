package org.app.core.ads.callback

abstract class AdsCallback {
    open fun onShow() {}
    open fun onClosed() {}
    open fun onError(message: String?) {}
    open fun getReward(amount: Int, type: String) {}
}