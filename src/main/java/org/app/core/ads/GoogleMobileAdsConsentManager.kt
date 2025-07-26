package org.app.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

class GoogleMobileAdsConsentManager private constructor(context: Context) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)
    
    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }
    
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()
    
    val isPrivacyOptionsRequired: Boolean
        get() =
            consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    
    fun gatherConsent(
        activity: Activity,
        isDebug: Boolean = false,
        hashedId: String = "1CB0E995D54C54B6F8F1EC34A01AC2F2",
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener
    ) {
        val params = if (isDebug) {
            // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
            val debugSettings =
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(hashedId)
                    .build()
            ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings)
                .build()
        } else {
            ConsentRequestParameters.Builder()
                .build()
        }
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                }
            },
            { requestConsentError ->
                onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
            }
        )
    }
    
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    }
    
    companion object {
        @Volatile private var instance: GoogleMobileAdsConsentManager? = null
        
        fun getInstance(context: Context) =
            instance
                ?: synchronized(this) {
                    instance ?: GoogleMobileAdsConsentManager(context).also { instance = it }
                }
    }
}
