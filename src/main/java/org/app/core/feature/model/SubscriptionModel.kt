package org.app.core.feature.model

import android.content.Context
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import org.app.core.R
import org.app.core.feature.iap.BillingRepository.Companion.TAG
import timber.log.Timber

data class SubscriptionModel(
    val id: String,
    val name: String,
    val formatPrice: String,
    val currencyCode: String,
    val priceAmount: Long,
    val period: BillingPeriod,
    val discount: Int = 60,
    var state: Int = Purchase.PurchaseState.UNSPECIFIED_STATE
) {

    enum class BillingPeriod {
        NONE, P1W, P1M, P3M, P6M, P1Y
        ;

        fun title(context: Context) = when(this) {
            P1M -> context.getString(R.string.txt_premium_monthly_package)
            P1Y -> context.getString(R.string.txt_premium_yearly_package)
            else -> ""
        }

        companion object {
            fun safeValueOf(value: String) = try {
                valueOf(value)
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        fun fromProductDetails(productDetails: ProductDetails, context: Context): SubscriptionModel? {
            val pricingPhase = productDetails.subscriptionOfferDetails?.get(0)?.pricingPhases?.pricingPhaseList?.firstOrNull { it.priceAmountMicros > 0 } ?: return null
            val formatPrice = pricingPhase.formattedPrice
            val currencyCode = pricingPhase.priceCurrencyCode
            val priceAmount = pricingPhase.priceAmountMicros
            val period = BillingPeriod.safeValueOf(pricingPhase.billingPeriod) ?: return null
            val id = productDetails.productId

            return SubscriptionModel(
                id,
                period.title(context),
                formatPrice,
                currencyCode,
                priceAmount,
                period
            )
        }
    }
}
