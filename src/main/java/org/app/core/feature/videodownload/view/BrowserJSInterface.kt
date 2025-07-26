package org.app.core.feature.videodownload.view

import android.webkit.JavascriptInterface
import android.webkit.WebView

class BrowserJSInterface(private val onVideoClickCallback: (String, String) -> Unit) {

    companion object {
        private const val JS_NAME = "BrowserWB"
    }

    fun attachToWebVIew(webView: WebView) {
        webView.addJavascriptInterface(this, JS_NAME)
    }

    @JavascriptInterface
    fun onVideoClicked(url: String, thumbnail: String) {
        this.onVideoClickCallback(url, thumbnail)
    }

}