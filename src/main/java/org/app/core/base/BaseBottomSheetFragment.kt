package org.app.core.base

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import com.google.android.gms.ads.AdSize
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.ads.base.NativeStyle
import org.app.core.ads.callback.LoadCallback
import org.app.core.ads.remoteconfig.CoreRemoteConfig
import org.app.core.base.extensions.calculateBannerHeightBy
import org.app.core.base.extensions.hide
import org.app.core.base.extensions.setMargins
import org.app.core.base.utils.StringResId
import org.app.core.base.utils.px
import timber.log.Timber
import java.lang.reflect.ParameterizedType
import kotlin.math.min

abstract class BaseBottomSheetFragment<VB : ViewBinding> :
    BottomSheetDialogFragment(){

    open val TAG = this::class.simpleName ?: "BaseBottomSheetFragment"
    private var _isShown = false
    var adsContainer: FrameLayout? = null
    var layoutCard: CardView? = null

    protected lateinit var binding: VB

    override fun getTheme() = R.style.SheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val type = javaClass.genericSuperclass
        val clazz = (type as ParameterizedType).actualTypeArguments[0] as Class<*>
        val method = clazz.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        )
        binding = method.invoke(null, layoutInflater, container, false) as VB
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.SheetDialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initDialog()
        showAds()
    }

    override fun onStart() {
        super.onStart()

        _isShown = true
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        _isShown = false
    }

    fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    abstract fun initDialog()

    fun showAds() {
        adsContainer ?: return
        layoutCard ?: return
        if (CoreAds.instance.isHideAds) {
            layoutCard?.hide()
            return
        }

        val actv = activity ?: return
        val remoteConfig = CoreRemoteConfig.instance.adsRemoteConfig
        if (remoteConfig == null || remoteConfig.status == false) {
            return
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
            CoreAds.instance.loadOrShowAdmobNativeAds(
                adsContainer!!,
                nativeAds.id!!,
                nativeAds.event ?: tagNative,
                nativeAds.style ?: NativeStyle.BIG_10,
                object : LoadCallback() {
                    override fun onLoadSuccess() {
                        Timber.tag("NativeAdmob").i( "Callback onLoadSuccess111 $_isShown")
                        if (_isShown && adsContainer != null) {
                            CoreAds.instance.showAdmobNativeAds(adsContainer, nativeAds.style ?: NativeStyle.BIG_10)
                        }
                    }
                }
            )
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
                CoreAds.instance.showAdapterBannerAds(
                    actv,
                    adsContainer!!,
                    bannerAds.id!!,
                    bannerAds.event ?: tagBanner,
                    size,
                    bannerAds.collapsible_type,
                    object : LoadCallback() {
                        override fun onLoadSuccess() {
                            Timber.tag("BannerAdmob").i( "onLoadSuccess...$_isShown")
                            if (_isShown && adsContainer != null) {
                                CoreAds.instance.showAvailableBanner(adsContainer!!, bannerAds.id!!, bannerAds.event ?: tagBanner, size)
                            }
                        }
                    }
                )
            }
        }
    }
}