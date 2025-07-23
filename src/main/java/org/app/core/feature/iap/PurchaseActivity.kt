package org.app.core.feature.iap

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.base.BaseActivity
import org.app.core.base.binding.setOnSingleClickListener
import org.app.core.base.extensions.disable
import org.app.core.base.extensions.enable
import org.app.core.base.extensions.showMessage
import org.app.core.databinding.ActivityPurchaseBinding
import org.app.core.feature.model.SubscriptionModel
import timber.log.Timber

@AndroidEntryPoint
class PurchaseActivity : BaseActivity<ActivityPurchaseBinding>() {
    private val viewModel: PurchaseViewModel by viewModels()

    override fun getLayoutId() = R.layout.activity_purchase

    private var _needRefresh: Boolean = false
    private var _from: String = ""

    override fun setUpViews() {
        binding.viewModel = viewModel
        _from = intent.extras?.getString(KEY_FROM) ?: ""

        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        binding.closeBtn.setOnSingleClickListener {
            finish()
        }
        binding.laterBtn.setOnSingleClickListener {
            finish()
        }

        binding.upgradeBtn.setOnSingleClickListener {
            _needRefresh = viewModel.onPurchase(this)
            CoreAds.instance.logFirebaseEvent("IAPLaunching_$_from")
        }
    }

    override fun onResume() {
        super.onResume()

        if (_needRefresh) {
            Timber.tag("IAP_TAG").i("onResume!!!")
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.getPurchasedProducts()?.let {
                    CoreAds.instance.logFirebaseEvent("IAPSuccess_$_from")
                    finish()
                } ?: kotlin.run {
                    CoreAds.instance.logFirebaseEvent("IAPFailed_$_from")
                    Timber.tag("IAP_TAG").i("onResume not finish --> Why???")
                }
            }, 250)
        }

        Handler(Looper.getMainLooper()).postDelayed({

            try {
                val items = viewModel.uiState.value.products
                Timber.tag("IAP_TAG").i("Timeout: ${items.size}")
                if (items.isEmpty()) {
                    showMessage(getString(R.string.txt_timeout_billing_load))
                    hideProgressDialog()
                    finish()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }, 10000)
    }

    override fun setUpObserver() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiState.collectLatest { state ->
                    if (state.loading) {
                        showProgressDialog()
                    } else {
                        hideProgressDialog()
                    }
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: PurchaseUiState) {
        binding.monthTv.text = state.monthlyTitle(this)
        binding.yearlyTv.text = state.yearlyTitle(this)
        binding.monthlyPrice.text = state.monthlyPrice(this)
        binding.yearlyPrice.text = state.yearlyPrice(this)
        when(state.purchased) {
            SubscriptionModel.BillingPeriod.NONE -> {
                binding.upgradeBtn.enable()
                binding.upgradeBtn.text = getString(R.string.txt_try_free_trial)
            }
            SubscriptionModel.BillingPeriod.P1M -> {
                binding.upgradeBtn.disable()
                binding.upgradeBtn.text = getString(R.string.txt_already_monthly_premium)
                binding.monthlyPackage.isClickable = false
                binding.yearlyPackage.isClickable = false
                binding.yearlyPackage.disable()
            }
            SubscriptionModel.BillingPeriod.P1Y -> {
                binding.upgradeBtn.disable()
                binding.upgradeBtn.text = getString(R.string.txt_already_yearly_premium)
                binding.yearlyPackage.isClickable = false
                binding.monthlyPackage.isClickable = false
                binding.monthlyPackage.disable()
            }
            else -> {}
        }

        if (state.products.isEmpty()) {
            binding.monthlyPackage.isClickable = false
            binding.monthlyPackage.disable()
            binding.yearlyPackage.isClickable = false
            binding.yearlyPackage.disable()
            binding.upgradeBtn.disable()
        }
    }

    companion object {
        const val KEY_FROM = "key_start_from"

        fun open(context: Context, from: String = "onboard") {
            val intent = Intent(context, PurchaseActivity::class.java)
            intent.putExtra(KEY_FROM, from)
            context.startActivity(intent)
        }
    }
}