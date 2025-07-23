package org.app.core.feature.iap

import android.app.Activity
import android.app.Application
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.app.core.base.BaseViewModel
import org.app.core.feature.model.SubscriptionModel
import org.app.core.feature.model.SubscriptionModel.BillingPeriod
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val application: Application,
    private val billingRepository: BillingRepository
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            billingRepository.getAvailableSubscriptions()
                .collectLatest { products ->
                    Timber.tag("IAP_TAG").i("getAvailableSubscriptions: ${products.size}")

                    val purchased = products.firstOrNull { it.state == Purchase.PurchaseState.PURCHASED }?.period ?: BillingPeriod.NONE
                    var selected = _uiState.value.selected
                    if (purchased != BillingPeriod.NONE) {
                        selected = purchased
                    }
                    _uiState.update {
                        it.copy(
                            products = products,
                            loading = false,
                            purchased = purchased,
                            selected = selected
                        )
                    }
                }
        }
    }

    fun getPurchasedProducts() = billingRepository.purchasedProducts()

    fun onSelectPlan(isYearly: Boolean) {
        _uiState.update {
            it.copy(selected = if (isYearly) BillingPeriod.P1Y else BillingPeriod.P1M)
        }
    }

    fun onPurchase(activity: Activity) : Boolean {
        val state = _uiState.value
        val productId = state.products.firstOrNull { it.period == state.selected }?.id ?: return false
        billingRepository.launchSubscriptionFlow(activity, productId)

        return true
    }
}