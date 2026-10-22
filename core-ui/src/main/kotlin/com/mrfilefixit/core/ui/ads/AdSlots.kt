package com.mrfilefixit.core.ui.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The only screens allowed to show a banner. */
enum class BannerPlacement { HOME, EXAM_PICKER, FILE_LIST }

/**
 * What features know about ads. Implemented by :core-ads and provided by :app, so feature modules
 * never depend on the ads SDK.
 */
interface AdSlots {
    @Composable
    fun Banner(placement: BannerPlacement, modifier: Modifier)

    /**
     * Called after a job succeeds and before its result is shown. May show an interstitial.
     * Must return promptly and never throw; if no ad is ready the result opens immediately.
     */
    suspend fun beforeResult(activity: Activity)

    /** Opt-in rewarded ad for extra batch jobs. Returns true only if the reward was earned. */
    suspend fun showRewarded(activity: Activity): Boolean
}

object NoAds : AdSlots {
    @Composable
    override fun Banner(placement: BannerPlacement, modifier: Modifier) = Unit

    override suspend fun beforeResult(activity: Activity) = Unit

    override suspend fun showRewarded(activity: Activity): Boolean = false
}

val LocalAdSlots = staticCompositionLocalOf<AdSlots> { NoAds }
