package org.app.core.base

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdSize
import com.zeugmasolutions.localehelper.LocaleHelper
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegate
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.app.core.base.utils.NetworkUtil
import org.app.core.base.utils.getDialogWaiting
import org.app.core.base.widget.CustomToast
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.setMargins
import org.app.core.base.extensions.show
import org.app.core.base.utils.px
import java.util.*
import kotlinx.coroutines.isActive
import org.app.core.base.utils.hideLoadingDialog
import org.app.core.base.utils.showLoadingDialog


abstract class BaseActivity<VB : ViewDataBinding> : AppCompatActivity() {
    open val TAG = this::class.simpleName

    private val localeDelegate: LocaleHelperActivityDelegate = LocaleHelperActivityDelegateImpl()

    private var _binding: VB? = null
    val mBinding: VB?
        get() = _binding

    open val binding get() = _binding!!

    val ensureBindingNotNull get() = _binding

    private val progressDialog: Dialog by lazy { getDialogWaiting(this) }

    private var isInternetConnected = true

    private var toastNoInternet: Toast? = null

    private var toastHasInternet: Toast? = null

    var adsContainer: FrameLayout? = null
    var layoutCard: CardView? = null
    private var _timeStamp: Long = 0
    private var _hasNativeAds: Boolean = true

    open fun preventShowToastNoInternet() = false

    open fun toastPaddingBottom(): Int? = null

    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGrantedMap ->
        val filter = isGrantedMap.values.filter { !it }

        if (filter.isEmpty()) {
            onPermissionResult?.invoke((true))
        } else {
            onPermissionResult?.invoke((false))
        }
    }

    override
    fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val context = super.createConfigurationContext(overrideConfiguration)
        return LocaleHelper.onAttach(context)
    }

    override
    fun getApplicationContext(): Context =
        localeDelegate.getApplicationContext(super.getApplicationContext())

    override
    fun onResume() {
        super.onResume()
        localeDelegate.onResumed(this)
    }

    override
    fun onPause() {
        super.onPause()
        localeDelegate.onPaused()
    }

    override
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViewBinding()
        setContentView(binding.root)
        _timeStamp = 0

        if (savedInstanceState == null) {
            setUpBottomNavigation()
        }

        toastNoInternet = CustomToast.makeText(
            this,
            getString(R.string.no_internet_connection_title),
            R.drawable.ic_common_error
        )
        toastHasInternet = CustomToast.makeText(
            this,
            getString(R.string.has_internet_connection_title),
            R.drawable.ic_tick_correct,
            toastPaddingBottom()
        )
        isInternetConnected = NetworkUtil.isNetworkConnected(this)

        setUpViews()
        setUpObserver()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                showAds(1)
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onActivityStarted()
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (CoreAds.instance.isHideAds) {
                    _hasNativeAds = false
                    layoutCard?.hide()
                } else {
                    while (isActive && _hasNativeAds) {
                        if (shouldRefreshAds()) {
                            refreshNative()
                        }
                        delay(1000)
                    }
                }
            }
        }
    }

    open fun setUpObserver(){}

    override
    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        setUpBottomNavigation()
    }

    protected open fun getOption(tag: String?): FragmentControllerOption {
        return FragmentControllerOption.Builder()
            .setTag(tag)
            .useAnimation(false)
            .addBackStack(false)
            .isTransactionReplace(true)
            .option
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

    private fun initViewBinding() {
        _binding = DataBindingUtil.setContentView(this, getLayoutId())
        binding.lifecycleOwner = this
        binding.executePendingBindings()
    }

    @LayoutRes
    abstract fun getLayoutId(): Int

    open fun setUpBottomNavigation() {}

    open fun setUpViews() {}

    // Override this function for task that need run background and may need time to completed
    open fun onActivityStarted() {}

    open fun updateLocale(language: String) {
        localeDelegate.setLocale(this, Locale(language))
    }

    override
    fun getDelegate() = localeDelegate.getAppCompatDelegate(super.getDelegate())

    protected open fun getChildLayoutReplace(): Int {
        return 0
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    fun onNetworkStateChanged(isConnected: Boolean) {
        if (isConnected != isInternetConnected) {
            isInternetConnected = isConnected
            if (isConnected) {
                showAds()
            }
        }
    }

    fun showProgressDialog() {
        try {
            ensureBindingNotNull?.apply {
                root.post {
                    if (!progressDialog.isShowing) progressDialog.show()
                }
            }
        } catch (_: Exception) {}
    }

    fun hideProgressDialog() {
        try {
            ensureBindingNotNull?.apply {
                root.post {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                }
            }
        } catch (_: Exception) {}
    }

    fun showToastHasInternet() {
        // show if activity resume
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            toastHasInternet?.show()
        }
    }

    fun hideToastHasInternet() {
        toastHasInternet?.cancel()
    }

    fun showToastNoInternet() {
        toastNoInternet?.show()
    }

    fun showAds(preloads: Int = 0) : Boolean {
        adsContainer ?: return false
        layoutCard ?: return false

        if (CoreAds.instance.isHideAds) {
            layoutCard?.hide()
            return false
        }

        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: CoreRemoteConfig.instance.fetchLocalConfig(this)
        remoteConfig ?: return false

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }
        layoutCard!!.show()
        if (nativeAds != null) {
            layoutCard!!.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            layoutCard!!.setMargins(left = 12.px, right =  12.px)
            layoutCard!!.radius = 10.px.toFloat()
            CoreAds.instance.showAdapterNativeAdsMultiple(
                this.applicationContext,
                this,
                adsContainer!!,
                nativeAds.id!!,
                tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                false,
                nativeAds.preload ?: 0)

            _timeStamp = System.currentTimeMillis()
            return true
        } else {
            val tagBanner = TAG + "_Banner"
            val bannerAds = remoteConfig.banners?.firstOrNull {
                it.tag == tagBanner && !it.id.isNullOrBlank()
            }
            if (bannerAds != null) {
                if (bannerAds.size == "medium") {
                    layoutCard!!.layoutParams.apply {
                        width = 300.px
                    }
                    layoutCard!!.radius = 10.px.toFloat()
                } else {
                    layoutCard!!.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    layoutCard!!.setMargins(left = 0, right =  0)
                    layoutCard!!.radius = 0f
                }
                val banner = CoreAds.instance.showAdapterBannerAds(
                    this,
                    adsContainer!!,
                    bannerAds.id!!,
                    tagBanner,
                    if (bannerAds.size == "medium") AdSize.MEDIUM_RECTANGLE else null,
                    null,
                    if (_timeStamp == 0L) bannerAds.collapsible_type else null,
                    true
                )
                _hasNativeAds = false
                _timeStamp = System.currentTimeMillis()
                if (!CoreAds.instance.isHideAds && banner == null) {
                    showProgressDialog()
                    Handler(Looper.getMainLooper())
                        .postDelayed({
                            hideProgressDialog()
                        }, 1500)
                }
                return true
            }
        }

        layoutCard!!.hide()
        return false
    }

    private fun refreshNative() {
        adsContainer ?: return
        layoutCard ?: return

        if (CoreAds.instance.isHideAds) {
            _hasNativeAds = false
            layoutCard?.hide()
            return
        }

        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: CoreRemoteConfig.instance.fetchLocalConfig(this)
        remoteConfig ?: return

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }
        layoutCard!!.show()
        if (nativeAds != null) {
            CoreAds.instance.showAdapterNativeAdsMultiple(
                this.applicationContext,
                this,
                adsContainer!!,
                nativeAds.id!!,
                tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                false,
                nativeAds.preload ?: 0)

            _timeStamp = System.currentTimeMillis()
        }
    }

    fun requestPermissions(permissions: Array<String>, onCompleted: ((Boolean) -> Unit)? = null) {
        val remainingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (remainingPermissions.isNotEmpty()) {
            onPermissionResult = onCompleted
            requestPermissionLauncher.launch(remainingPermissions.toTypedArray())
        } else {
            onCompleted?.invoke((true))
        }
    }

    private fun shouldRefreshAds() : Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime >= (_timeStamp + 25000)
    }
}