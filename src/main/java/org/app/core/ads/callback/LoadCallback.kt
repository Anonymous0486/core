package org.app.core.ads.callback

abstract class LoadCallback {
    open fun onLoadSuccess() {}
    open fun onLoadFailed(message: String?) {}
}