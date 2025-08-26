package org.app.core.feature.videodownload.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import org.app.core.feature.CoreFeature
import timber.log.Timber

class FacebookBrowserWebView : WebView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    var onVideoClicked: ((String, String) -> Unit)? = null

    init {
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        settings.userAgentString = DEFAULT_MOBILE_AGENT
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.databaseEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setRendererPriorityPolicy(RENDERER_PRIORITY_BOUND, true)
    }

    fun runJsBrowser(isStory: Boolean = false) {
//        BrowserJSInterface { url, thumbnail ->
//            onVideoClicked?.invoke(url, thumbnail)
//        }.attachToWebVIew(this)

        try {
            val localScript = if (isStory) CoreFeature.instance.getStoryParseJs() else CoreFeature.instance.getFBVideoParseJs()
            this.evaluateJavascript("javascript:${localScript}") {
                Timber.tag("###DEBUG").i("Finish run js: $it")
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun runDomParser(onCompleted: () -> Unit) {
        try {
            val localScript = CoreFeature.instance.getDomParseJs()
            this.evaluateJavascript("javascript:${localScript}") {
                Timber.tag("###DEBUG").i("Finish parse DOM: $it")
                onCompleted.invoke()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            onCompleted.invoke()
        }
    }

    fun runThreadsParser() {
        try {
            this.evaluateJavascript("javascript:${jsScript}") {
                Timber.tag("###DEBUG").i("Finish Threads parser: $it")
            }
        } catch (ex: Exception) {
            Firebase.crashlytics.recordException(ex)
            ex.printStackTrace()
        }
    }

    companion object {
        const val DEFAULT_MOBILE_AGENT =
//            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
            "Mozilla/5.0 (Linux; Android 13; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"

        val jsScript = """
            var presentImg = '';
            var videoTit = '';
            function checkVisible(elm) {
                var rect = elm.getBoundingClientRect();
                var viewHeight = Math.max(document.documentElement.clientHeight, window.innerHeight);
                return !(rect.bottom < 0 || rect.top - viewHeight >= 0);
            }
            
            var videoImg = document.querySelectorAll('meta[property="og:image"]');
            if (videoImg.length > 0) {
                presentImg = videoImg[0].content;
            }
            var videoDesc = document.querySelectorAll('meta[property="og:description"]');
            if (videoDesc.length > 0) {
                videoTit = videoDesc[0].content;
            }
            var myVideos = document.getElementsByTagName('video');
            if (myVideos.length > 0) {
                mJava.onVideoClicked(myVideos[0].src, presentImg, videoTit);
            } else {
                mJava.onVideoClicked('', presentImg, videoTit);
            }
            """
    }
}