package org.app.core.base

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.binding.setOnSingleClickListener
import org.app.core.base.extensions.calculateBannerHeightBy
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.setMargins
import org.app.core.base.extensions.show
import org.app.core.base.utils.NetworkUtil
import org.app.core.base.utils.StringResId
import org.app.core.base.utils.getDialogWaiting
import org.app.core.base.utils.px
import org.app.core.base.widget.CustomToast
import timber.log.Timber
import java.util.*
import kotlin.math.min

abstract class BaseActivity<VB : ViewDataBinding> : AppCompatActivity() {
    open val TAG = this::class.simpleName ?: "BaseActivityTAG"

    open val nativeHeight: Int = 0

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

    var nativeFullContainer: FrameLayout? = null
    var nativeFullId: String = ""
    var adsContainer: FrameLayout? = null
    var layoutCard: CardView? = null
    var closeNativeFullAds: ImageView? = null
    private var _timeStamp: Long = 0
    private var _hasNativeAds: Boolean = true
    private var _firstTimeShownBanner: Boolean = true
    private var _requestNativeId = ""
    private var _pendingBackAction: Boolean = false

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

//    override fun startActivity(intent: Intent?) {
//        val options = ActivityOptionsCompat.makeCustomAnimation(
//            this,
//            R.anim.anim_slide_in_top,
//            R.anim.fade_out
//        )
//        startActivity(intent, options.toBundle())
//    }

    override
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViewBinding()
        setContentView(binding.root)
        _timeStamp = System.currentTimeMillis()

        if (savedInstanceState == null) {
            setUpBottomNavigation()
        }

//        toastNoInternet = CustomToast.makeText(
//            this,
//            getString(R.string.no_internet),
//            R.drawable.ic_no_internet
//        )
//        toastHasInternet = CustomToast.makeText(
//            this,
//            getString(R.string.connection_restored),
//            R.drawable.ic_tick_correct,
//            toastPaddingBottom()
//        )
        isInternetConnected = NetworkUtil.isNetworkConnected(this)

        setupBinding()
        setUpViews()
        setUpObserver()

        if (CoreAds.instance.isHideAds) {
            _hasNativeAds = false
            layoutCard?.hide()
        } else {
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    CoreAds.instance.nativeLoadedTs.collectLatest { ts ->
                        Timber.tag(TAG).d("NativeAdmob loaded: $ts - $_timeStamp")
                        if (ts >_timeStamp) {
                            onNativeLoaded()
                            if (nativeFullContainer?.isVisible == true && nativeFullId.isNotBlank()) {
                                showNativeFull("Refresh")
                            } else {
                                showAds()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onActivityStarted()
                showAds(true)
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

        preloadSwitchScreenAds()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (this.javaClass.simpleName == "HomeActivity") {
            return super.onOptionsItemSelected(item)
        }

        return when (item.itemId) {
            android.R.id.home -> {
                handleBackPress()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    open fun setupBinding() {
        Timber.tag(TAG).i("setupBinding")
        nativeFullContainer = _binding?.root?.findViewById(R.id.nativeFullContainer) as? FrameLayout
        adsContainer = _binding?.root?.findViewById(R.id.adsContainer) as? FrameLayout
        layoutCard = _binding?.root?.findViewById(R.id.layoutCard) as? CardView
        closeNativeFullAds = _binding?.root?.findViewById(R.id.closeNativeFullAds) as? ImageView

        nativeFullContainer?.hide()
        closeNativeFullAds?.hide()
    }

    open fun setUpViews() {
        if (closeNativeFullAds != null) {
            closeNativeFullAds?.setOnSingleClickListener {
                nativeFullContainer?.hide()
                closeNativeFullAds?.hide()
                if (_pendingBackAction) {
                    finish()
                } else {
                    onCloseAction()
                }
            }
        }
    }

    open fun onSecurityCheck() {}

    open fun onCloseAction() {
        finish()
    }

    // Override this function for task that need run background and may need time to completed
    open fun onActivityStarted() {}

    open fun onNativeLoaded() {}

    fun showBottomBanner(parent: CardView, container: FrameLayout) {
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            parent.hide()
        }
        val tagBanner = TAG + "_BottomBanner"
        val bannerAds = remoteConfig?.banners?.firstOrNull {
            it.tag == tagBanner && !it.id.isNullOrBlank()
        }
        if (bannerAds != null) {
            CoreAds.instance.showAdapterBannerAds(
                this,
                container,
                bannerAds.id!!,
                bannerAds.event ?: tagBanner,
                null,
                null,
                null,
                true
            )
        } else {
            parent.hide()
        }
    }

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
        CoreAds.instance.releaseNativeAds(_requestNativeId)
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
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                ensureBindingNotNull?.apply {
                    root.post {
                        if (!progressDialog.isShowing) progressDialog.show()
                    }
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

//    override fun finish() {
//        super.finish()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            overrideActivityTransition(
//                OVERRIDE_TRANSITION_OPEN,
//                R.anim.no_change,     // previous activity enter
//                R.anim.anim_slide_out_top  // current activity exit
//            )
//        } else {
//            @Suppress("DEPRECATION")
//            overridePendingTransition(
//                R.anim.no_change,
//                R.anim.anim_slide_out_top
//            )
//        }
//    }

    fun enableEdgeToEdge(root: View, isFull: Boolean = true) {
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isFull) {
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val isVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            Timber.tag("####DEBUG").i("setOnApplyWindowInsetsListener...$isVisible")
            if (isVisible) {
                if (!isFull) {
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars().or(WindowInsetsCompat.Type.ime()))
                    v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = insets.bottom
                        topMargin = insets.top
                    }
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    fun hideStatusBar(view: View) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    fun hideNavigationBar(view: View) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = bars.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun showAds(loading: Boolean = false) : Boolean {
        adsContainer ?: return false
        layoutCard ?: return false

        if (CoreAds.instance.isHideAds) {
            layoutCard?.hide()
            return false
        }

        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            return false
        }

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }

        Timber.tag(TAG).i("Showing ads...")
        if (nativeAds != null) {
            layoutCard!!.show()
            layoutCard!!.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            layoutCard!!.setMargins(left = 12.px, right =  12.px)
            layoutCard!!.radius = 10.px.toFloat()
            if (nativeHeight >= 0) {
                resources.displayMetrics.let { displayMetrics ->
                    val height = displayMetrics.heightPixels
                    val maxH = if (nativeHeight == 0) {
                        min((1 * (height - 24.px) / 3), 350.px)
                    } else {
                        nativeHeight
                    }
                    adsContainer!!.viewTreeObserver
                        .addOnGlobalLayoutListener(
                            OnViewGlobalLayoutListener(adsContainer!!, maxH)
                        )
                }
            }

            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                this.applicationContext,
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
            )
            if (aNative != null) {
                _timeStamp = System.currentTimeMillis()
                _requestNativeId = aNative.requestId
            } else {
                if (loading) {
                    showProgressDialog()
                    Handler(Looper.getMainLooper())
                        .postDelayed({
                            hideProgressDialog()
                        }, 1500)
                }
            }
            return true
        } else {
            val tagBanner = TAG + "_Banner"
            val bannerAds = remoteConfig.banners?.firstOrNull {
                it.tag == tagBanner && !it.id.isNullOrBlank()
            }
            _hasNativeAds = false
            if (bannerAds != null) {
                val size = if (bannerAds.size == "medium") {
                    layoutCard!!.layoutParams.apply {
                        width = 300.px
                    }
                    layoutCard!!.radius = 10.px.toFloat()
                    AdSize.MEDIUM_RECTANGLE
                } else if (bannerAds.size == "full") {
                    layoutCard!!.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    layoutCard!!.setMargins(left = 0, right =  0)
                    layoutCard!!.radius = 0f
                    AdSize.FULL_BANNER
                }  else if (bannerAds.size == "inline") {
                    val size = calculateBannerHeightBy()
                    layoutCard!!.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }

                    layoutCard!!.setMargins(left = 24.px, right =  24.px)
                    layoutCard!!.radius = 10.px.toFloat()
                    size
                } else {
                    layoutCard!!.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    layoutCard!!.setMargins(left = 0, right =  0)
                    layoutCard!!.radius = 0f
                    null
                }
                val banner = CoreAds.instance.showAdapterBannerAds(
                    this,
                    adsContainer!!,
                    bannerAds.id!!,
                    bannerAds.event ?: tagBanner,
                    size,
                    null,
                    if (_firstTimeShownBanner) bannerAds.collapsible_type else null,
                    true
                )
                _timeStamp = System.currentTimeMillis()
                _firstTimeShownBanner = false

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

        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            return
        }

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }
        layoutCard!!.show()
        if (nativeAds != null) {
            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                this.applicationContext,
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
            )
            if (aNative != null) {
                _timeStamp = System.currentTimeMillis()
                _requestNativeId = aNative.requestId
            }
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

    fun handleBackPress() {
        val tagNative = TAG + "_BackAction"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            it.tag == tagNative
        }
        if (ads?.id.isNullOrBlank() || rmConfig?.status == false) {
            finish()
            return
        }

        val ret = CoreAds.instance.showAdapterInterstitialAds(
            ads?.timelapse ?: 0,
            getString(StringResId.loadingAds),
            this,
            ads?.id ?: return,
            ads.event ?: "DummyTranslateVoice",
            object : AdsCallback() {

                override fun onClosed() {
                    super.onClosed()

                    if (!_pendingBackAction) {
                        if (!isDestroyed && !isFinishing) {
                            finish()
                        }
                    }
                }

                override fun onError(message: String?) {
                    super.onError(message)

                    if (!_pendingBackAction) {
                        if (!isDestroyed && !isFinishing) {
                            finish()
                        }
                    }
                }
            })

        if (ret) {
            val nativeId = ads.nativeId
            if (!nativeId.isNullOrBlank() && nativeFullContainer != null) {
                _pendingBackAction = true
                nativeFullId = nativeId
                showNativeFull("BackAction")
            }
        }
    }

    fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?) {
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            it.tag == tag
        }

        if (ads?.id.isNullOrBlank() || rmConfig?.status == false) {
            onCompleted?.invoke()
            return
        }

        var nativeId = ""
        val ret = CoreAds.instance.showAdapterInterstitialAds(
            ads?.timelapse ?: 0,
            getString(StringResId.loadingAds),
            this,
            ads?.id!!,
            ads.event ?: "ClickGuideDummy",
            object : AdsCallback() {
                override fun onClosed() {
                    super.onClosed()
                    Timber.tag("MONET-DEBUG").i("Inter onClosed -> $nativeId")
                    if (nativeId.isBlank()) {
                        if (!isDestroyed && !isFinishing) {
                            onCompleted?.invoke()
                        }
                    }
                }

                override fun onError(message: String?) {
                    super.onError(message)
                    Timber.tag("MONET-DEBUG").i("Inter onError -> $nativeId")
                    nativeFullId = ""
                    if (!isDestroyed && !isFinishing) {
                        onCompleted?.invoke()
                    }
                }

                override fun onShow() {
                    Timber.tag("MONET-DEBUG").i("Inter shown!")
                    if (!isDestroyed && !isFinishing) {
                        if (nativeId.isNotBlank() && nativeFullContainer != null) {
                            nativeFullId = nativeId
                            showNativeFull(tag)
                        }
                    }
                }
            })

        if (ret) {
            nativeId = ads.nativeId ?: ""
        }
    }

    fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?) {
        val tagBack = TAG + "_Back"
        val tagNext = TAG + "_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            it.tag == tagBack || it.tag == tagNext
        }
        if (ads?.id.isNullOrBlank()) {
            onCompleted?.invoke()
            return
        }
        CoreAds.instance.showAdapterInterstitialAds(
            ads.timelapse ?: 0,
            getString(StringResId.loadingAds),
            this,
            ads.id ?: return,
            ads.event ?: "DummyEvent",
            object : AdsCallback() {

                override fun onClosed() {
                    super.onClosed()

                    if (!isDestroyed && !isFinishing) {
                        onCompleted?.invoke()
                    }
                }

                override fun onError(message: String?) {
                    super.onError(message)

                    if (!isDestroyed && !isFinishing) {
                        onCompleted?.invoke()
                    }
                }
                override fun onShow() {
                    Timber.tag("MONET-DEBUG").i("Inter shown!")
                    if (!isDestroyed && !isFinishing) {
                        onShown?.invoke()
                    }
                }
            })
    }

    private fun showNativeFull(tag: String) {
        Timber.tag(TAG).i("showNativeFull $tag")
        nativeFullContainer?.show()
        closeNativeFullAds?.show()
        val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
            this.applicationContext,
            nativeFullContainer!!,
            nativeFullId,
            tag + "NativeFull",
            NativeStyle.FULLSCREEN,
        )
        if (aNative != null) {
            nativeFullId = ""
        }
    }

    private fun shouldRefreshAds() : Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime >= (_timeStamp + 30000)
    }

    fun preloadNativeIfNeed() {
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (rmConfig == null || rmConfig.status == false) {
            return
        }

        val nativeAds = rmConfig.natives?.firstOrNull {
            it.place_preload == TAG && !it.id.isNullOrBlank()
        }

        if (nativeAds != null) {
            CoreAds.instance.preloadAdmobNativeAds(applicationContext, nativeAds.id!!, nativeAds.event ?: "DUMMY")
        }
    }

    private fun preloadSwitchScreenAds() {
        val tagBack = TAG + "_Back"
        val tagNext = TAG + "_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            (it.tag == tagBack || it.tag == tagNext) && it.always_preload == true
        }
        if (ads?.id.isNullOrBlank()) {
            return
        }

        Timber.tag("MONET-DEBUG").d("Preload inter ads in create activity!!!")
        CoreAds.instance.initAdapterInterstitialAds(this, ads.id!!, ads.event ?: "", 2)
    }
}