package org.app.core.base

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.extensions.setMargins
import org.app.core.base.utils.hideLoadingDialog
import org.app.core.base.utils.px
import org.app.core.base.utils.showLoadingDialog
import java.util.*
import kotlinx.coroutines.isActive
import org.app.core.R
import org.app.core.ads.CoreAds.Companion
import org.app.core.ads.callback.AdsCallback
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.config.AdsConfigure
import org.app.core.base.binding.setOnSingleClickListener
import org.app.core.base.extensions.calculateBannerHeightBy
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.show
import org.app.core.base.utils.NetworkUtil
import org.app.core.base.utils.StringResId
import timber.log.Timber
import kotlin.math.min

abstract class BaseFragment<VB : ViewDataBinding> : Fragment() {
    open val TAG = this::class.simpleName ?: "BaseFragmentTAG"
    open val nativeHeight: Int = 0
    open val showInitializeLoading: Boolean = true

    private var _binding: VB? = null
    val mBinding: VB?
        get() = _binding

    open val binding get() = _binding!!
    private var mRootView: View? = null
    private var hasInitializedRootView = false
    private var progressDialog: Dialog? = null
    private var isInternetConnected = true

    private var job: Job? = null
    var adsContainer: FrameLayout? = null
    var layoutCard: CardView? = null
    private var _timeStamp: Long = 0
    private var _refreshTimelapse: Long = 30000
    private var _hasNativeAds: Boolean = true
    private var _hasBannerAds: Boolean = true
    private var _firstTimeShownBanner: Boolean = true
    private var _requestNativeId = ""
    var nativeFullContainer: FrameLayout? = null
    var closeNativeFullAds: ImageView? = null
    var nativeFullId: String = ""
    private var _pendingBackAction: Boolean = false
    private var _firstDisplay = true

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
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (mRootView == null) {
            initViewBinding(inflater, container)
        }

        initView(binding.root)
        _timeStamp = System.currentTimeMillis()
        // using when fragment transition animation 300ms
        binding.root.postDelayed(
            { initDataWithAnimation() },
            300
        )
        initObserver()

        return mRootView
    }

    private fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?) {
        _binding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)

        mRootView = binding.root
        binding.lifecycleOwner = viewLifecycleOwner
        binding.executePendingBindings()
    }

    override
    fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Use LifecycleObserver instead of override lifecycle methods such as onResume
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                onFragmentResume()
            }
            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)

                _firstDisplay = false
                onFragmentPause()
            }
        })

        if (!hasInitializedRootView) {
            getFragmentArguments()
            setBindingVariables()
            observeAPICall()
            setupObservers()
            setUpViews()

            hasInitializedRootView = true
        }

        isInternetConnected = NetworkUtil.isNetworkConnected(context ?: return)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (CoreAds.instance.isHideAds) {
                    _hasNativeAds = false
                    _hasBannerAds = false
                    layoutCard?.hide()
                } else {
                    if (_firstDisplay) {
                        showAds(showInitializeLoading)
                    }
                    while (isActive && _hasNativeAds) {
                        if (shouldRefreshAds()) {
                            refreshNative()
                        }
                        delay(1000)
                    }
                }
            }
        }

        preloadAds()
    }

    override fun onDestroyView() {
        if (view?.parent != null) {
            (view?.parent as? ViewGroup)?.endViewTransition(view)
        }
        hideLoading()

        CoreAds.instance.releaseNativeAds(_requestNativeId)
        super.onDestroyView()
    }

    protected open fun getOption(tag: String?): FragmentControllerOption {
        return FragmentControllerOption.Builder()
            .setTag(tag)
            .useAnimation(true)
            .addBackStack(true)
            .isTransactionReplace(true)
            .option
    }

    protected open fun getOption(tag: String?, isReplaceFrag: Boolean): FragmentControllerOption {
        return FragmentControllerOption.Builder()
            .setTag(tag)
            .useAnimation(true)
            .addBackStack(true)
            .isTransactionReplace(isReplaceFrag)
            .option
    }

    @LayoutRes
    abstract fun getLayoutId(): Int

    open fun initView(view: View) {}

    open fun initDataWithAnimation() {}

    open fun getFragmentArguments() {}

    open fun setBindingVariables() {
        Timber.tag(TAG).i("setupBinding")
        nativeFullContainer = _binding?.root?.findViewById(R.id.nativeFullContainer) as? FrameLayout
        adsContainer = _binding?.root?.findViewById(R.id.adsContainer) as? FrameLayout
        layoutCard = _binding?.root?.findViewById(R.id.layoutCard) as? CardView
        closeNativeFullAds = _binding?.root?.findViewById(R.id.closeNativeFullAds) as? ImageView

        nativeFullContainer?.hide()
        closeNativeFullAds?.hide()
        if (closeNativeFullAds != null) {
            closeNativeFullAds?.setOnSingleClickListener {
                nativeFullContainer?.hide()
                closeNativeFullAds?.hide()
                onCloseAction()
            }
        }
    }

    open fun setUpViews() {}

    open fun onFragmentResume() {}

    open fun onFragmentPause() {}

    open fun observeAPICall() {}

    open fun setupObservers() {

    }

    open fun onCloseAction() {}

    protected open fun onRetryClick() {}

    protected open fun reloadData() {}

    fun onNetworkStateChanged(isConnected: Boolean) {
        if (isConnected != isInternetConnected) {
            isInternetConnected = isConnected
            if (isConnected) {
                showAds()
            }
        }
    }

    fun showLoading() {
        hideLoading()
        progressDialog = showLoadingDialog(activity, null)
    }

    fun showLoading(hint: String?) {
        hideLoading()
        try {
            progressDialog = showLoadingDialog(activity, hint)
        } catch (_: Exception) {}
    }

    fun hideLoading() = hideLoadingDialog(progressDialog, activity)

    fun setLanguage(language: String) {
        (activity as? BaseActivity<*>)?.updateLocale(language)
    }

    open fun initObserver(){}

    open fun showMessage(message : String){
        Toast.makeText(requireContext(),message,Toast.LENGTH_SHORT).show()
    }

    val currentLanguage: Locale
        get() = Locale.getDefault()

    @SuppressLint("LogNotTimber")
    fun showAds(showLoading: Boolean = false) : Boolean {
        adsContainer ?: return false
        layoutCard ?: return false
        if (CoreAds.instance.isHideAds) {
            layoutCard?.hide()
            _hasNativeAds = false
            _hasBannerAds = false
            return false
        }

        val actv = activity ?: return false
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            return false
        }

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }

        Timber.tag(TAG).i( "$TAG Show ads...")
        if (nativeAds != null) {
            layoutCard!!.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            layoutCard!!.setMargins(left = 16.px, right = 16.px)
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

            _hasNativeAds = true
            _hasBannerAds = false
            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        Timber.tag("NativeAdmob").i( "Callback onLoadSuccess111 ${lifecycle.currentState}")
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && adsContainer != null) {
                            val retAds = CoreAds.instance.showAdmobNativeAds(adsContainer, nativeAds.style ?: NativeStyle.BIG_10)
                            _timeStamp = System.currentTimeMillis()
                            retAds?.let { _requestNativeId = it.requestId }
                        }
                    }
                }
            )
            if (aNative != null) {
                _timeStamp = System.currentTimeMillis()
                _requestNativeId = aNative.requestId
            } else {
                if (showLoading) {
                    showLoading(getString(StringResId.loading))
                    Handler(Looper.getMainLooper())
                        .postDelayed({
                            hideLoading()
                        }, 1500)
                }
            }

            return true
        } else {
            val tagBanner = TAG + "_Banner"
            val bannerAds = remoteConfig.banners?.firstOrNull {
                it.tag == tagBanner && !it.id.isNullOrBlank()
            }
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
                    val size = context?.calculateBannerHeightBy()
                    if (size != null) {
                        layoutCard!!.layoutParams.apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    } else {
                        layoutCard!!.layoutParams.apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    }

                    layoutCard!!.setMargins(left = 16.px, right = 16.px)
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
                    actv,
                    adsContainer!!,
                    bannerAds.id!!,
                    bannerAds.event ?: tagBanner,
                    size,
                    if (_firstTimeShownBanner) bannerAds.collapsible_type else null,
                    object : LoadCallback() {
                        override fun onLoadSuccess() {
                            Timber.tag("BannerAdmob").i( "onLoadSuccess...${lifecycle.currentState}")
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && adsContainer != null) {
                                CoreAds.instance.showAvailableBanner(adsContainer!!, bannerAds.id!!, bannerAds.event ?: tagBanner, size)
                            }
                        }
                    }
                )
                _firstTimeShownBanner = false
                _hasNativeAds = false
                _hasBannerAds = true

                if (banner == null && showLoading) {
                    showLoading(getString(StringResId.loading))
                    Handler(Looper.getMainLooper())
                        .postDelayed({
                            hideLoading()
                        }, 1500)
                }
                return true
            }
        }

        _hasBannerAds = false
        _hasNativeAds = false
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
        Timber.tag("###DEBUG").i( "Refresh ads...")
        if (nativeAds != null) {
            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && adsContainer != null) {
                            val retAds = CoreAds.instance.showAdmobNativeAds(adsContainer, nativeAds.style ?: NativeStyle.BIG_10)
                            retAds?.let { _requestNativeId = it.requestId }
                            _timeStamp = System.currentTimeMillis()
                        }
                    }
                }
            )
            if (aNative != null) {
                _timeStamp = System.currentTimeMillis()
                _requestNativeId = aNative.requestId
            }
        }
    }

    fun requestPermissions(permissions: Array<String>, onCompleted: ((Boolean) -> Unit)? = null) {
        activity?.let { ctx ->
            val remainingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (remainingPermissions.isNotEmpty()) {
                onPermissionResult = onCompleted
                requestPermissionLauncher.launch(remainingPermissions.toTypedArray())
            } else {
                onCompleted?.invoke((true))
            }
        } ?: kotlin.run {
            onCompleted?.invoke((false))
        }
    }

    fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?) {
        if (activity == null) {
            onCompleted?.invoke()
            return
        }

        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (rmConfig == null) {
            onCompleted?.invoke()
            return
        }

        val ads = rmConfig.interstitials?.firstOrNull {
            it.tag == tag
        }

        if (ads?.id.isNullOrBlank()) {
            onCompleted?.invoke()
            return
        }

        var nativeId: String? = null
        val ret = CoreAds.instance.showAdapterInterstitialAds(
            ads?.timelapse ?: 0,
            getString(StringResId.loadingAds),
            requireActivity(),
            ads?.id!!,
            ads.event ?: "ClickGuideDummy",
            object : AdsCallback() {
                override fun onClosed() {
                    super.onClosed()
                    Timber.tag("MONET-DEBUG").i("Inter onClosed -> $nativeId")
                    if (nativeId.isNullOrBlank() || nativeFullContainer == null) {
                        if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                            onCompleted?.invoke()
                        }
                    }
                }

                override fun onError(message: String?) {
                    super.onError(message)
                    nativeFullId = ""
                    Timber.tag("MONET-DEBUG").i("Inter onClosed -> $nativeId")
                    if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                        onCompleted?.invoke()
                    }
                }

                override fun onShow() {
                    Timber.tag("MONET-DEBUG").i("Inter shown!")
                    if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                        if (!nativeId.isNullOrBlank() && nativeFullContainer != null) {
                            nativeFullId = nativeId!!
                            showNativeFull(tag)
                        }
                    }
                }
            })

        if (ret) {
            nativeId = ads.nativeId
        }
    }

    fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?) {
        val tagBack = TAG + "_Back"
        val tagNext = TAG + "_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            it.tag == tagBack || it.tag == tagNext
        }
        if (ads?.id.isNullOrBlank() || activity == null) {
            onCompleted?.invoke()
            return
        }
        CoreAds.instance.showAdapterInterstitialAds(
            ads.timelapse ?: 0,
            getString(StringResId.loadingAds),
            requireActivity(),
            ads.id ?: return,
            ads.event ?: "DummyTranslateVoice",
            object : AdsCallback() {
                override fun onClosed() {
                    super.onClosed()

                    onCompleted?.invoke()
                }

                override fun onError(message: String?) {
                    super.onError(message)

                    onCompleted?.invoke()
                }

                override fun onShow() {
                    Timber.tag("MONET-DEBUG").i("Fragment Inter shown!")
                    onShown?.invoke()
                }
            })
    }

    fun preloadNativeIfNeed() {
        val actv = activity ?: return
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (rmConfig == null || rmConfig.status == false) {
            return
        }

        val nativeAds = rmConfig.natives?.firstOrNull {
            it.tag == TAG && !it.id.isNullOrBlank()
        }

        if (nativeAds != null) {
            CoreAds.instance.preloadAdmobNativeAds(actv.applicationContext, nativeAds.id!!, nativeAds.event ?: "DUMMY")
        }
    }

    private fun preloadAds() {
        // Inter
        val tagBack = TAG + "_Back"
        val tagNext = TAG + "_Next"
        val rmConfig = CoreRemoteConfig.instance.adsRemoteConfig
        val ads = rmConfig?.interstitials?.firstOrNull {
            it.tag == tagBack || it.tag == tagNext
        }
        if (ads?.id.isNullOrBlank()) {
            return
        }

        Timber.tag("MONET-DEBUG").d("Preload inter ads in create fragment!!!")
        CoreAds.instance.initAdapterInterstitialAds(activity ?: return, ads.id!!, ads.event ?: "")

        // Native
        val tagNative = TAG
        val nativeAds = rmConfig.natives?.firstOrNull {
            it.place_preload == tagNative
        }
        if (nativeAds != null && nativeAds.id.isNullOrBlank() == false) {
            Timber.tag("MONET-DEBUG").d("Preload Native ads in create fragment!!!")
            CoreAds.instance.loadOrShowAdmobNativeAds(
                null,
                nativeAds.id!!,
                nativeAds.event ?: "DummyEventNative",
                nativeAds.style ?: NativeStyle.BIG_13
            )
        }
    }

    private fun showNativeFull(tag: String) {
        activity ?: return
        nativeFullContainer?.show()
        closeNativeFullAds?.show()
        val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
            nativeFullContainer!!,
            nativeFullId,
            tag + "NativeFull",
            NativeStyle.FULLSCREEN,
            object : LoadCallback() {
                override fun onLoadSuccess() {
                    if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                        val retAds = CoreAds.instance.showAdmobNativeAds(nativeFullContainer, NativeStyle.FULLSCREEN)
                        retAds?.let { _requestNativeId = it.requestId }
                    }
                }
            }
        )
        if (aNative != null) {
            nativeFullId = ""
        }
    }

    private fun shouldRefreshAds() : Boolean {
        val currentTime = System.currentTimeMillis() - _refreshTimelapse
        return currentTime >= _timeStamp
    }
}
