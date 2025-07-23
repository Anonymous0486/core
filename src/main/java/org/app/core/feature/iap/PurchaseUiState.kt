package org.app.core.feature.iap

import android.content.Context
import android.text.SpannableStringBuilder
import androidx.core.content.res.ResourcesCompat
import org.app.core.R
import org.app.core.base.extensions.append
import org.app.core.base.extensions.getColorR
import org.app.core.feature.model.SubscriptionModel
import org.app.core.feature.model.SubscriptionModel.BillingPeriod

data class PurchaseUiState(
    val loading: Boolean = true,
    val purchased: BillingPeriod = BillingPeriod.NONE,
    val selected: BillingPeriod = BillingPeriod.P1Y,
    val products: List<SubscriptionModel> = emptyList(),
) {

    fun isYearly() = selected == BillingPeriod.P1Y

    fun isMonthly() = selected == BillingPeriod.P1M

    fun yearlyTitle(context: Context) : SpannableStringBuilder {
        val typeface = if (selected == BillingPeriod.P1Y) {
            ResourcesCompat.getFont(context, R.font.roboto_bold)
        } else {
            ResourcesCompat.getFont(context, R.font.roboto_regular)
        }
        val prefix = "".append(
            context.getString(R.string.txt_premium_yearly_package),
            context.getColorR(R.color.colorGray_08),
            typeface
        )
        val suffix = "".append(
            " (${context.getString(R.string.txt_save)} 60%)",
            context.getColorR(R.color.redef4444),
            ResourcesCompat.getFont(context, R.font.roboto_bold)
        )

        return prefix.append(suffix)
    }

    fun monthlyTitle(context: Context) : SpannableStringBuilder {
        val typeface = if (selected == BillingPeriod.P1M) {
            ResourcesCompat.getFont(context, R.font.roboto_bold)
        } else {
            ResourcesCompat.getFont(context, R.font.roboto_regular)
        }
        return "".append(
            context.getString(R.string.txt_premium_monthly_package),
            context.getColorR(R.color.colorGray_08),
            typeface
        )
    }

    fun monthlyPrice(context: Context) : SpannableStringBuilder  {
        val price = products.firstOrNull { it.period == BillingPeriod.P1M }?.formatPrice ?: "\$2.99"

        val typeface = if (selected == BillingPeriod.P1M) {
            ResourcesCompat.getFont(context, R.font.roboto_bold)
        } else {
            ResourcesCompat.getFont(context, R.font.roboto_regular)
        }
        return "".append(
            price + "/" + context.getString(R.string.txt_month),
            if (selected == BillingPeriod.P1M) context.getColorR(R.color.colorPrimary) else context.getColorR(R.color.colorGray_08),
            typeface
        )
    }

    fun yearlyPrice(context: Context): SpannableStringBuilder {
        val price = products.firstOrNull { it.period == BillingPeriod.P1Y }?.formatPrice ?: "\$21.50"

        val typeface = if (selected == BillingPeriod.P1Y) {
            ResourcesCompat.getFont(context, R.font.roboto_bold)
        } else {
            ResourcesCompat.getFont(context, R.font.roboto_regular)
        }
        return "".append(
            price + "/" + context.getString(R.string.txt_year),
            if (selected == BillingPeriod.P1Y) context.getColorR(R.color.colorPrimary) else context.getColorR(R.color.colorGray_08),
            typeface
        )
    }

    fun confirmTitle(context: Context) : String {
        val currencyCode = products.firstOrNull()?.currencyCode ?: "$"
        return if (selected == BillingPeriod.P1Y) {
            context.getString(R.string.txt_try_free_trial) + " 0$currencyCode"
        } else {
            context.getString(R.string.txt_continue)
        }
    }
}
