package com.mrfilefixit.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Google UMP consent. Ads are requested only when [canRequestAds] is true. */
@Singleton
internal class ConsentManager @Inject constructor(@ApplicationContext context: Context) {
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean get() = consent.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Refreshes consent status and shows the consent form if the user still has to answer it.
     * Returns whether ads may be requested. Errors are not fatal: a previous answer may still allow ads.
     */
    suspend fun gather(activity: Activity): Boolean {
        val updated = suspendCancellableCoroutine { cont ->
            consent.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (cont.isActive) cont.resume(true) },
                { if (cont.isActive) cont.resume(false) },
            )
        }
        if (updated) {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { if (cont.isActive) cont.resume(Unit) }
            }
        }
        return canRequestAds
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}
