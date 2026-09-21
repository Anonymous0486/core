package org.app.core.base

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
import org.app.core.ads.base.AdsViewContainer
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.base.ScreenAdsDelegate
import org.app.core.ads.callback.AdsActionHandler
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.binding.setOnSingleClickListener
import org.app.core.base.extensions.calculateBannerHeightBy
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.setMargins
import org.app.core.base.extensions.show
import org.app.core.base.utils.NetworkUtil
import org.app.core.base.utils.StringResId
import org.app.core.base.utils.getDialogWaiting
import org.app.core.base.utils.hideLoadingDialog
import org.app.core.base.utils.px
import timber.log.Timber
import java.util.Locale
import kotlin.math.min

abstract class BaseActivity<VB : ViewDataBinding> : AppCompatActivity(), AdsActionHandler {
    open val TAG = this::class.simpleName ?: "BaseActivityTAG"

    open val nativeHeight: Int = 0

    open val showInitializeLoading: Boolean = true

    private val localeDelegate: LocaleHelperActivityDelegate = LocaleHelperActivityDelegateImpl()

    private var _binding: VB? = null
    val mBinding: VB?
        get() = _binding

    open val binding get() = _binding!!

    val ensureBindingNotNull get() = _binding

    val minAdsActiveState: Lifecycle.State get() = Lifecycle.State.STARTED
    protected val adsViews = AdsViewContainer()
    private val adsDelegate by lazy {
        ScreenAdsDelegate(
            lifecycleOwnerProvider = { this },
            activityProvider = { this },
            viewContainerProvider = { adsViews },
            screenTagProvider = { TAG },
            nativeHeightProvider = { nativeHeight },
            minActiveStateProvider = { minAdsActiveState },
            initializeLoading = { showInitializeLoading },
            onShowLoading = { show -> if (show) showProgressDialog() else hideProgressDialog() }
        )
    }

    private val progressDialog: Dialog by lazy { getDialogWaiting(this) }

    private var isInternetConnected = true

    var nativeFullId: String = ""
    private var _pendingBackAction: Boolean = false

    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGrantedMap ->
        val filter = isGrantedMap.values.filter { !it }

        if (filter.isEmpty()) {
            onPermissionResult?.invoke(true)
        } else {
            onPermissionResult?.invoke(false)
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
        if (savedInstanceState == null) {
            setUpBottomNavigation()
        }
        isInternetConnected = NetworkUtil.isNetworkConnected(this)

        setupBinding()
        setUpViews()
        setUpObserver()
        adsDelegate.attachLifecycle(this)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onActivityStarted()
            }
        }
    }

    open fun setUpObserver(){}

    override
    fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        setUpBottomNavigation()
    }

    override fun showAds(showLoading: Boolean) = adsDelegate.showAds(showLoading)
    override fun refreshNative() = adsDelegate.refreshNative()
    override fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?) = adsDelegate.showInterstitialBy(tag, onCompleted)
    override fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?) = adsDelegate.handleSwitchScreen(onShown, onCompleted)
    override fun preloadAds() = adsDelegate.preloadAds()

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
        _binding?.root?.let { root ->
            adsViews.adsContainer = root.findViewById(R.id.adsContainer)
            adsViews.layoutCard = root.findViewById(R.id.layoutCard)
            adsViews.nativeFullContainer = root.findViewById(R.id.nativeFullContainer)

            (root.findViewById(R.id.closeNativeFullAds) as? ImageView)?.let { btn ->
                adsViews.closeNativeFullAds = btn
                btn.setOnSingleClickListener {
                    root.findViewById<FrameLayout>(R.id.nativeFullContainer)?.hide()
                    btn.hide()

                    if (_pendingBackAction) {
                        finish()
                    } else {
                        onCloseAction()
                    }
                }
            }
        }
    }

    open fun setUpViews() {

    }

    open fun onSecurityCheck() {}

    open fun onCloseAction() {
        finish()
    }

    // Override this function for task that need run background and may need time to completed
    open fun onActivityStarted() {}

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
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            CoreAds.instance.showAvailableBanner(container, bannerAds.id!!, bannerAds.event ?: tagBanner, null)
                        }
                    }
                }
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
        adsViews.clear()
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

    fun enableEdgeToEdge(root: View, isFull: Boolean = true) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        ))
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
            if (!nativeId.isNullOrBlank()) {
                _pendingBackAction = true
                nativeFullId = nativeId
            }
        }
    }
}