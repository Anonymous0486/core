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
import org.app.core.ads.remoteconfig.config.AdsConfigure
import org.app.core.base.binding.setOnSingleClickListener
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.show
import org.app.core.base.utils.NetworkUtil
import org.app.core.base.utils.StringResId
import timber.log.Timber
import kotlin.math.min

abstract class BaseFragment<VB : ViewDataBinding> : Fragment() {
    open val TAG = this::class.simpleName ?: "BaseFragmentTAG"
    open val forceMaxHeightNative: Boolean = false

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
    private var _hasNativeAds: Boolean = true
    private var _hasBannerAds: Boolean = true
    private var _requestNativeId = ""
    var nativeFullContainer: FrameLayout? = null
    var closeNativeFullAds: ImageView? = null
    var nativeFullId: String = ""
    private var _pendingBackAction: Boolean = false

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

                onFragmentPause()
            }
        })

        _timeStamp = System.currentTimeMillis()
        if (!hasInitializedRootView) {
            getFragmentArguments()
            setBindingVariables()
            observeAPICall()
            setupObservers()
            setUpViews()

            hasInitializedRootView = true
        }
        view.setOnTouchListener { _, _ -> true }
        isInternetConnected = NetworkUtil.isNetworkConnected(context ?: return)
        if (CoreAds.instance.isHideAds) {
            _hasNativeAds = false
            _hasBannerAds = false
            layoutCard?.hide()
        } else {
            showAds(true)
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    CoreAds.instance.nativeLoadedTs.collectLatest { ts ->
                        if (ts >_timeStamp) {
                            if (nativeFullContainer?.isVisible == true && nativeFullId.isNotBlank()) {
                                showNativeFull("Refresh")
                            } else {
                                if (_hasNativeAds) showAds()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive && _hasNativeAds) {
                    if (shouldRefreshAds()) {
                        refreshNative()
                    }
                    delay(1000)
                }
            }
        }
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

    open fun setupObservers() {}

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
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: CoreRemoteConfig.instance.fetchLocalConfig(actv)
        remoteConfig ?: return false

        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }

        Timber.tag(TAG).i( "$TAG Show ads...")
        if (nativeAds != null) {
            layoutCard!!.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            layoutCard!!.setMargins(left = 12.px, right =  12.px)
            layoutCard!!.radius = 10.px.toFloat()
            if (!forceMaxHeightNative) {
                resources.displayMetrics.let { displayMetrics ->
                    val height = displayMetrics.heightPixels
                    val maxH = min(((height - 24.px)  / 3), 350.px)
                    layoutCard!!.viewTreeObserver
                        .addOnGlobalLayoutListener(
                            OnViewGlobalLayoutListener(layoutCard!!, maxH)
                        )
                }
            }
            _hasNativeAds = true
            _hasBannerAds = false
            val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
                actv.applicationContext,
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
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
                        }, 1600)
                }
            }

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
                    actv,
                    adsContainer!!,
                    bannerAds.id!!,
                    bannerAds.event ?: tagBanner,
                    if (bannerAds.size == "medium") AdSize.MEDIUM_RECTANGLE else null,
                    null,
                    if (_timeStamp == 0L) bannerAds.collapsible_type else null,
                    true
                )
                _timeStamp = System.currentTimeMillis()
                _hasNativeAds = false
                _hasBannerAds = true

                if (!CoreAds.instance.isHideAds && banner == null) {
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

        val actv = activity ?: return
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: CoreRemoteConfig.instance.fetchLocalConfig(actv)
        remoteConfig ?: return
        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }
        Timber.tag("###DEBUG").i( "Refresh ads...")
        if (nativeAds != null) {
            CoreAds.instance.showAdapterNativeAdsMultiple(
                actv.applicationContext,
                actv,
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                false,
                nativeAds.preload ?: 0)
            _timeStamp = System.currentTimeMillis()
        }
    }

    fun showAdsWhenPagerChanged(container: FrameLayout, parent: CardView) {
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig ?: CoreRemoteConfig.instance.fetchLocalConfig(requireActivity())
        remoteConfig ?: return

        val actv = activity ?: return
        val tagNative = TAG + "_Native"
        val nativeAds = remoteConfig.natives?.firstOrNull {
            it.tag == tagNative && !it.id.isNullOrBlank()
        }

        if (nativeAds != null) {
            parent.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            parent.setMargins(left = 12.px, right =  12.px)
            parent.radius = 10.px.toFloat()
            CoreAds.instance.showAdapterNativeAdsMultiple(
                actv.applicationContext,
                actv,
                container,
                nativeAds.id!!,
                nativeAds.event ?: (tagNative + "Dummy"),
                nativeAds.style ?: NativeStyle.BIG_10,
                false,
                nativeAds.preload ?: 0)
        } else {
            val tagBanner = TAG + "_Banner"
            val bannerAds = remoteConfig.banners?.firstOrNull {
                it.tag == tagBanner && !it.id.isNullOrBlank()
            }
            if (bannerAds != null) {
                if (bannerAds.size == "medium") {
                    parent.layoutParams.apply {
                        width = 300.px
                    }
                    parent.radius = 10.px.toFloat()
                } else {
                    parent.layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    parent.setMargins(left = 0, right =  0)
                    parent.radius = 0f
                }
                CoreAds.instance.showAdapterBannerAds(
                    actv,
                    container,
                    bannerAds.id!!,
                    bannerAds.event ?: (tagBanner + "Dummy"),
                    if (bannerAds.size == "medium") AdSize.MEDIUM_RECTANGLE else null,
                    null,
                    bannerAds.collapsible_type,
                    true
                )
            } else {
                parent.hide()
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
        val ads = rmConfig?.interstitials?.firstOrNull {
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
                    if (nativeId.isNullOrBlank()) {
                        if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                            onCompleted?.invoke()
                        }
                    }
                }

                override fun onError(message: String?) {
                    super.onError(message)
                    if (nativeId.isNullOrBlank()) {
                        if (activity != null && !requireActivity().isDestroyed && !requireActivity().isFinishing) {
                            onCompleted?.invoke()
                        }
                    }
                }
            })

        if (ret) {
            nativeId = ads.nativeId
            if (!nativeId.isNullOrBlank() && nativeFullContainer != null) {
                nativeFullId = nativeId
                showNativeFull(tag)
            }
        }
    }

    private fun showNativeFull(tag: String) {
        activity ?: return
        nativeFullContainer?.show()
        closeNativeFullAds?.show()
        val aNative = CoreAds.instance.loadOrShowAdmobNativeAds(
            requireActivity().applicationContext,
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
        return currentTime >= (_timeStamp + 25000)
    }
}