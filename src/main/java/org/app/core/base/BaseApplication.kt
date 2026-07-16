package org.app.core.base

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import com.zeugmasolutions.localehelper.LocaleAwareApplication
import org.app.core.base.utils.NetworkUtil
import timber.log.Timber
import java.lang.ref.WeakReference

abstract class BaseApplication : LocaleAwareApplication() {

    companion object {
        private lateinit var sInstance: BaseApplication

        @JvmStatic
        fun getInstance(): BaseApplication {
            return sInstance
        }

        @JvmStatic
        fun checkNetwork(): Boolean {
            return NetworkUtil.isNetworkAvailable(getInstance())
        }

        @JvmStatic
        fun getStringResource(@StringRes res: Int): String {
            return sInstance.getString(res)
        }
    }

    private var data: MutableMap<String, WeakReference<Any>>? = HashMap()
    private val _isBlurred by lazy { MutableLiveData<Boolean>() }
    var isBlurred: MutableLiveData<Boolean> = _isBlurred

    override fun onCreate() {
        super.onCreate()
        sInstance = this
    }

    fun getAppContext(): Context {
        return sInstance.applicationContext
    }

    fun setData(id: String, `object`: Any) {
        data!![id] = WeakReference(`object`)
        Timber.d("SIZE: ${data!!.size}")
    }

    fun getData(id: String): Any? {
        if (data != null) {
            val objectWeakReference = data!![id]
            if (objectWeakReference != null) {
                return objectWeakReference.get()
            }
        } else {
            return null
        }
        return null
    }

    fun notifyBlurShown() {
        _isBlurred.postValue(true)
    }

    fun notifyBlurHide() {
        _isBlurred.postValue(false)
    }

    open fun getOpenAdsId(): String? = null

    open fun getListTestDeviceId(): List<String> = listOf()
}