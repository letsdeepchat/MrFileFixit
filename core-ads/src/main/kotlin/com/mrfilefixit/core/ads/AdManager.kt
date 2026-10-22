package com.mrfilefixit.core.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.mrfilefixit.core.domain.dispatchers.DispatcherProvider
import com.mrfilefixit.core.domain.logging.AppLogger
import com.mrfilefixit.core.ui.ads.AdSlots
import com.mrfilefixit.core.ui.ads.BannerPlacement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one place that talks to AdMob. Order is always: UMP consent -> MobileAds.initialize -> preload.
 *
 * Rules (see docs/ARCHITECTURE.md): banners only on [BannerPlacement]s; one interstitial after a successful
 * job, capped by [InterstitialPacing]; rewarded ads are opt-in and reward only on onUserEarnedReward.
 * Every failure path lets the user continue: an ad never blocks a result.
 */
@Singleton
internal class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: ConsentManager,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
) : AdSlots {
    private val pacing = InterstitialPacing(now = SystemClock::elapsedRealtime)
    private val initMutex = Mutex()
    private val ready = MutableStateFlow(false)
    private val isReady = ready.asStateFlow()

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var interstitialLoading = false
    private var rewardedLoading = false

    @Suppress("TooGenericExceptionCaught") // UMP can fail in many ways; none may crash the app.
    override suspend fun initialize(activity: Activity) {
        initMutex.withLock {
            if (ready.value) return
            val allowed = try {
                consent.gather(activity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(TAG) { "Consent failed: ${e.javaClass.simpleName}" }
                consent.canRequestAds
            }
            if (!allowed) return
            // initialize() does disk work; Google recommends calling it off the main thread.
            withContext(dispatchers.io) {
                val done = CompletableDeferred<Unit>()
                MobileAds.initialize(context) { done.complete(Unit) }
                withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { done.await() }
            }
            ready.value = true
        }
        preloadInterstitial()
        preloadRewarded()
    }

    override val privacyOptionsRequired: Boolean get() = consent.privacyOptionsRequired

    override fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)

    @Composable
    override fun Banner(placement: BannerPlacement, modifier: Modifier) {
        val adsReady by isReady.collectAsStateWithLifecycle()
        if (!adsReady || BuildConfig.BANNER_UNIT_ID.isEmpty()) return
        val density = LocalDensity.current
        val widthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { viewContext ->
                AdView(viewContext).apply {
                    adUnitId = BuildConfig.BANNER_UNIT_ID
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(viewContext, widthDp))
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }

    override suspend fun beforeResult(activity: Activity) {
        val ad = interstitial
        if (!ready.value || ad == null || !pacing.canShow()) {
            if (ad == null) preloadInterstitial()
            return
        }
        interstitial = null
        val closed = CompletableDeferred<Unit>()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = pacing.recordShown()

            override fun onAdDismissedFullScreenContent() {
                closed.complete(Unit)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                logger.warn(TAG) { "Interstitial failed to show: ${error.code}" }
                closed.complete(Unit)
            }
        }
        try {
            ad.show(activity)
            // The user decides when to close it; the timeout only guards against a lost callback.
            withTimeoutOrNull(SHOW_TIMEOUT_MILLIS) { closed.await() }
        } finally {
            preloadInterstitial()
        }
    }

    override suspend fun showRewarded(activity: Activity): Boolean {
        val ad = rewarded
        if (!ready.value || ad == null) {
            preloadRewarded()
            return false
        }
        rewarded = null
        var earned = false
        val closed = CompletableDeferred<Unit>()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                closed.complete(Unit)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                closed.complete(Unit)
            }
        }
        try {
            // The reward comes only from the SDK's earned-reward callback, never from a click.
            ad.show(activity) { earned = true }
            withTimeoutOrNull(SHOW_TIMEOUT_MILLIS) { closed.await() }
        } finally {
            preloadRewarded()
        }
        return earned
    }

    private fun preloadInterstitial() {
        if (!ready.value || interstitial != null || interstitialLoading ||
            BuildConfig.INTERSTITIAL_UNIT_ID.isEmpty()
        ) {
            return
        }
        interstitialLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    logger.debug(TAG) { "Interstitial load failed: ${error.code}" }
                }
            },
        )
    }

    private fun preloadRewarded() {
        if (!ready.value || rewarded != null || rewardedLoading || BuildConfig.REWARDED_UNIT_ID.isEmpty()) return
        rewardedLoading = true
        RewardedAd.load(
            context,
            BuildConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    rewardedLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    logger.debug(TAG) { "Rewarded load failed: ${error.code}" }
                }
            },
        )
    }

    private companion object {
        const val TAG = "Ads"
        const val INIT_TIMEOUT_MILLIS = 10_000L
        const val SHOW_TIMEOUT_MILLIS = 120_000L
    }
}
