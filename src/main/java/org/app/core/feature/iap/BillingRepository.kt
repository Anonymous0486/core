package org.app.core.feature.iap

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.app.core.R
import org.app.core.ads.CoreAds
import org.app.core.feature.extension.coroutinesIO
import org.app.core.feature.model.SubscriptionModel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext val context: Context,
) : PurchasesUpdatedListener {

    private val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    private val billingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enablePrepaidPlans()
                .enableOneTimeProducts()
                .build()
        )
        .setListener(this)
        .build()

    private val _productMap: MutableMap<String, ProductDetails> = HashMap()
    private val _subscriptions = mutableListOf<SubscriptionModel>()
    private var _purchasingActivity: Activity? = null
    private var _purchasingId: String? = null

    private var _initialized: Boolean = false
    private var _initializing: Boolean = true
    private var _isConnected: Boolean = false

    fun initialize() {
        val cachedPurchase = preference.getBoolean(PREFERENCE_KEY_IS_PURCHASED, false)
        Timber.tag(TAG).i("init billing client: $cachedPurchase")
        if (cachedPurchase) {
            CoreAds.instance.setHideAds(true)
        }
        setupBillingClient()
    }

    fun refreshIfNeed() {
        val cachedPurchase = preference.getBoolean(PREFERENCE_KEY_IS_PURCHASED, false)
        Timber.tag(TAG).i("refreshIfNeed: $cachedPurchase")
        if (cachedPurchase) {
            CoreAds.instance.setHideAds(true)
        }
        if (_isConnected) {
            if (_subscriptions.isEmpty()) {
                querySubscriptions()
            }
        } else {
            setupBillingClient()
        }
    }

    private fun setupBillingClient() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Timber.tag(TAG).i("onBillingServiceDisconnected")
                _isConnected = false
                setupBillingClient()
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Timber.tag(TAG).i("onBillingSetupFinished: ${billingResult.responseCode}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected = true
                    querySubscriptions()
                } else {
                    Timber.tag(TAG).e(billingResult.debugMessage)
                    _initializing = true
                    retryBillingServiceConnection()
                }
            }
        })
    }

    private fun retryBillingServiceConnection() {
        val maxTries = 3
        var tries = 1
        var isConnectionEstablished = false
        do {
            try {
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        _isConnected = false
                    }

                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            isConnectionEstablished = true
                            _isConnected = true
                            Timber.tag(TAG).i("Billing connection retry succeeded.")
                            querySubscriptions()
                        } else {
                            Timber.tag(TAG).e("Billing connection retry failed: ${billingResult.debugMessage}")
                        }
                    }
                })
            } catch (e: Exception) {
                e.message?.let {
                    Timber.tag(TAG).e(it)
                }
                tries++
            }
        } while (tries <= maxTries && !isConnectionEstablished)
    }

    // Query active subscriptions
    fun querySubscriptions() {
        coroutinesIO {
            _initializing = true
            val products = listOf(
                MONTHLY_SUBSCRIPTION_ID,
                LIFETIME_SUBSCRIPTION_ID
            ).map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build() }
            val params = QueryProductDetailsParams.newBuilder()
            params.setProductList(products)

            val product = billingClient.queryProductDetails(params.build())

            Timber.tag(TAG).i("querySubscriptions: ${product.billingResult.responseCode} - ${product.productDetailsList?.size}")
            if (product.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _subscriptions.clear()
                product.productDetailsList?.forEach { v ->
                    _productMap[v.productId] = v
                    SubscriptionModel.fromProductDetails(v, context)?.let { sub ->
                        _subscriptions.add(sub)
                    }
                }
                _initialized = true
                _initializing = false
                restorePurchases()
            } else {
                _initialized = true
                _initializing = false
            }

            Timber.tag(TAG).i("end querySubscriptions:${_subscriptions.size}")
        }
    }

    private fun restorePurchases() {
        coroutinesIO {
            val purchaseList = mutableListOf<Purchase>()

            // Query purchase list for subscription type
            val subParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)

            val subPurchasesResult = billingClient.queryPurchasesAsync(subParams.build())
            Timber.tag(TAG).i("QueryPurchases result: ${subPurchasesResult.billingResult.responseCode}, ${subPurchasesResult.purchasesList.size}")
            if (subPurchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchaseList.addAll(subPurchasesResult.purchasesList)
            }

            // Query purchase list for in-app one time type
//        val inAppParams = QueryPurchasesParams.newBuilder()
//            .setProductType(BillingClient.ProductType.INAPP)
//        val inAppPurchasesResult = billingClient.queryPurchasesAsync(inAppParams.build())
//        if (inAppPurchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//            purchaseList.addAll(inAppPurchasesResult.purchasesList)
//        }

            handlePurchase(purchaseList)
        }
    }

    private fun handlePurchase(purchases: List<Purchase>) {
        if (purchases.isNotEmpty()) {
            for (purchase in purchases) {
                val purchaseState = purchase.purchaseState

                purchase.products.forEach { item ->
                    Timber.tag(TAG).i("handlePurchase: $item - $purchaseState")

                    _subscriptions.firstOrNull { it.id == item }?.apply {
                        this.state = purchaseState
                    }
//                    if (item == _purchasingId) {
//                        _purchasingId = null
//                        finishPurchasingActivity()
//                    }
                }

                if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                    cachePurchaseState(true)

                    if (!purchase.isAcknowledged) {
                        startAcknowledgeProcess(purchase.purchaseToken)
                    }
                }
            }
        } else {
            Timber.tag(TAG).i("Empty purchase list!!!")
            cachePurchaseState(false)
            _subscriptions.forEach { it.state = Purchase.PurchaseState.UNSPECIFIED_STATE }
        }
        _initialized = true
        _initializing = false
    }

    private suspend fun acknowledgePurchase(purchaseToken: String): BillingResult = suspendCoroutine { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            continuation.resume(billingResult)
        }
    }

    private suspend fun acknowledgeWithRetry(purchaseToken: String, maxTries: Int = 3, initialDelay: Long = 2000L, retryFactor: Int = 2) {
        var currentDelay = initialDelay
        var attempts = 0

        while (attempts < maxTries) {
            val result = acknowledgePurchase(purchaseToken)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Timber.tag(TAG).i("Acknowledgement was successful")
                return
            } else {
                Timber.tag(TAG).e("Failed to acknowledge: ${result.debugMessage}, retrying...")

                delay(currentDelay)
                currentDelay *= retryFactor
                attempts++
            }
        }

        Timber.tag(TAG).e("Failed to acknowledge purchase after $maxTries attempts")
    }

    private fun startAcknowledgeProcess(purchaseToken: String) {
        coroutinesIO {
            try {
                acknowledgeWithRetry(purchaseToken)
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error during acknowledgement: ${e.message}")
            }
        }
    }

    fun getAvailableSubscriptions() = flow {
        while (!_initialized || _subscriptions.isEmpty()) {
            Timber.tag(TAG).i("getAvailableSubscriptions: $_initialized - $_initializing - ${_subscriptions.size}")

            if (!_initializing) {
                querySubscriptions()
            }
            delay(1000)
        }
        emit(_subscriptions)
    }

    fun launchSubscriptionFlow(activity: Activity, productId: String) {
        val productDetails = _productMap[productId] ?: return
        if (productDetails.subscriptionOfferDetails.isNullOrEmpty()) return

        val offers = productDetails.subscriptionOfferDetails
        var offerToken = if (offers != null && offers.size > 1) {
            offers.firstOrNull { it.offerTags.isNotEmpty() }?.offerToken ?: ""
        } else {
            if (offers.isNullOrEmpty()) "" else offers[0].offerToken
        }

        if (offerToken.isBlank()) {
            offerToken = offers?.firstOrNull { offer ->
                val pricingPhases = offer.pricingPhases.pricingPhaseList.firstOrNull { p -> p.billingPeriod == "P3D" }
                pricingPhases != null
            }?.offerToken ?: ""
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _purchasingActivity = activity
        _purchasingId = productId
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    fun purchasedProducts() = _subscriptions.firstOrNull { it.state == Purchase.PurchaseState.PURCHASED }

    fun isPurchased() : Boolean {
        val cachedPurchase = preference.getBoolean(PREFERENCE_KEY_IS_PURCHASED, false)
        Timber.tag(TAG).i("isPurchased: $cachedPurchase")
        if (cachedPurchase) {
            CoreAds.instance.setHideAds(true)
            return true
        }

        if (!_initialized) return false

        val filter = _subscriptions.firstOrNull { it.state == Purchase.PurchaseState.PURCHASED }
        return filter != null
    }

    fun getAllProducts() = _subscriptions

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Timber.tag(TAG).i("onPurchasesUpdated: ${purchases?.size} - ${result.responseCode}")
        if (!purchases.isNullOrEmpty()) {
            handlePurchase(purchases)
        }
    }

    private fun cachePurchaseState(state: Boolean) {
        Timber.tag(TAG).d("savePurchaseState: $state")

        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_KEY_IS_PURCHASED, state)
        editor.putLong(PREFERENCE_KEY_TIME_PURCHASED, System.currentTimeMillis())
        editor.apply()

        CoreAds.instance.setHideAds(state)
    }

    private fun getCachePurchaseState(): Boolean {
        return preference.getBoolean(PREFERENCE_KEY_IS_PURCHASED, false)
    }

    private fun finishPurchasingActivity() {
        Handler(Looper.getMainLooper()).post {
            (_purchasingActivity as? PurchaseActivity)?.finish()
        }
    }

    companion object {
        const val TAG = "IAP_TAG"
        const val MONTHLY_SUBSCRIPTION_ID = "monthly"
        const val LIFETIME_SUBSCRIPTION_ID = "lifetime"

        const val PREFERENCE_NAME = "IAP_Pref"
        const val PREFERENCE_KEY_IS_PURCHASED = "key_is_purchased"
        const val PREFERENCE_KEY_TIME_PURCHASED = "key_time_purchased"
    }
}