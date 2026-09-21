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
import org.app.core.ads.base.AdsViewContainer
import org.app.core.ads.base.ScreenAdsDelegate
import org.app.core.ads.callback.AdsActionHandler
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

abstract class BaseFragment<VB : ViewDataBinding> : Fragment(), AdsActionHandler {
    open val TAG = this::class.simpleName ?: "BaseFragmentTAG"
    open val nativeHeight: Int = 0
    open val showInitializeLoading: Boolean = true

    private var _binding: VB? = null
    val mBinding: VB? get() = _binding
    open val binding: VB
        get() = _binding ?: throw IllegalStateException(
            "Cannot access view binding when View is destroyed (between onDestroyView and onCreateView) at $TAG"
        )
    val ensureBindingNotNull: VB? get() = _binding

    val minAdsActiveState: Lifecycle.State get() = Lifecycle.State.RESUMED
    protected val adsViews = AdsViewContainer()
    private val adsDelegate by lazy {
        ScreenAdsDelegate(
            lifecycleOwnerProvider = { viewLifecycleOwner },
            activityProvider = { activity },
            viewContainerProvider = { adsViews },
            screenTagProvider = { TAG },
            nativeHeightProvider = { nativeHeight },
            minActiveStateProvider = { minAdsActiveState },
            initializeLoading = { showInitializeLoading },
            onShowLoading = { show -> if (show) showLoading() else hideLoading() }
        )
    }

    var adsContainer: FrameLayout? = null
    var layoutCard: CardView? = null
    var nativeFullContainer: FrameLayout? = null
    var closeNativeFullAds: ImageView? = null
    var nativeFullId: String = ""
    private var _requestNativeId: String = ""

    private var progressDialog: Dialog? = null
    private var isInternetConnected = true
    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { isGrantedMap ->
        val hasDenied = isGrantedMap.values.any { !it }
        onPermissionResult?.invoke(!hasDenied)
        onPermissionResult = null
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            super.onResume(owner)
            onFragmentResume()
        }
        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)

            _firstDisplay = false
            hideLoading()
            onFragmentPause()
        }
    }

    private var mRootView: View? = null
    private var _firstDisplay = true


    override
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DataBindingUtil.inflate(inflater, getLayoutId(), container, false)
        _binding?.lifecycleOwner = viewLifecycleOwner
        initView(binding.root)
        initObserver()

        return binding.root
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

        viewLifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        getFragmentArguments()
        setBindingVariables()
        observeAPICall()
        setupObservers()
        setUpViews()

        isInternetConnected = NetworkUtil.isNetworkConnected(context ?: return)
        adsDelegate.attachLifecycle(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        if (view?.parent != null) {
            (view?.parent as? ViewGroup)?.endViewTransition(view)
        }

        progressDialog = null
        onPermissionResult = null
        viewLifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        _binding = null
        adsViews.clear()

        super.onDestroyView()
    }

    override fun showAds(showLoading: Boolean) = adsDelegate.showAds(showLoading)
    override fun refreshNative() = adsDelegate.refreshNative()
    override fun showInterstitialBy(tag: String, onCompleted: (() -> Unit)?) = adsDelegate.showInterstitialBy(tag, onCompleted)
    override fun handleSwitchScreen(onShown: (() -> Unit)?, onCompleted: (() -> Unit)?) = adsDelegate.handleSwitchScreen(onShown, onCompleted)
    override fun preloadAds() = adsDelegate.preloadAds()

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

    open fun getFragmentArguments() {}

    open fun setBindingVariables() {
        Timber.tag(TAG).i("setupBinding")
        _binding?.root?.let { root ->
            adsViews.adsContainer = root.findViewById(R.id.adsContainer)
            adsViews.layoutCard = root.findViewById(R.id.layoutCard)
            adsViews.nativeFullContainer = root.findViewById(R.id.nativeFullContainer)
            adsViews.closeNativeFullAds = root.findViewById(R.id.closeNativeFullAds)

            (root.findViewById(R.id.closeNativeFullAds) as? ImageView)?.let { btn ->
                adsViews.closeNativeFullAds = btn
                btn.setOnSingleClickListener {
                    root.findViewById<FrameLayout>(R.id.nativeFullContainer)?.hide()
                    btn.hide()
                    onCloseAction()
                }
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
        try {
            progressDialog = showLoadingDialog(activity, null)
        } catch (_: Exception) {}
    }

    fun showLoading(hint: String?) {
        hideLoading()
        try {
            progressDialog = showLoadingDialog(activity, hint)
        } catch (_: Exception) {}
    }

    fun hideLoading() {
        hideLoadingDialog(progressDialog, activity)
        progressDialog = null
    }

    fun setLanguage(language: String) {
        (activity as? BaseActivity<*>)?.updateLocale(language)
    }

    open fun initObserver(){}

    open fun showMessage(message : String){
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    val currentLanguage: Locale
        get() = Locale.getDefault()

    fun requestPermissions(permissions: Array<String>, onCompleted: ((Boolean) -> Unit)? = null) {
        activity?.let { ctx ->
            val remainingPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (remainingPermissions.isNotEmpty()) {
                onPermissionResult = onCompleted
                requestPermissionLauncher.launch(remainingPermissions.toTypedArray())
            } else {
                onCompleted?.invoke(true)
            }
        } ?: kotlin.run {
            onCompleted?.invoke(false)
        }
    }
}
