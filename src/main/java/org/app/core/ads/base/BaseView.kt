package org.app.core.ads.base

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.app.core.feature.extension.doOnViewDrawn
import org.app.core.feature.extension.layoutInflater
import java.lang.reflect.ParameterizedType

abstract class BaseView<VB : ViewBinding> : FrameLayout {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    protected val binding by lazy(::getViewBinding)

    protected var viewScope: CoroutineScope? = null

    init {
        doOnViewDrawn {
            binding.onViewDrawn()
        }
    }

    private fun getViewBinding(): VB {
        val type = javaClass.genericSuperclass
        val clazz = (type as ParameterizedType).actualTypeArguments[0] as Class<*>
        val method = clazz.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        )
        return method.invoke(null, context.layoutInflater, this, true) as VB
    }

    protected open fun VB.onViewDrawn() {}

    @CallSuper
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.IO)
    }

    @CallSuper
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
    }
}
