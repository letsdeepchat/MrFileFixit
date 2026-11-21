package com.mrfilefixit.app.support

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** A tip as Play lists it: the product and its price in the user's currency ("₹49.00"). */
internal data class TipOption(val productId: String, val price: String, internal val details: ProductDetails)

/** What happened with a tip, for the Support screen. */
internal enum class TipEvent { THANKS, PENDING, FAILED, UNAVAILABLE }

/**
 * Optional tips through Google Play Billing, for the Play build, where Play's Payments policy requires it. Tips are
 * consumable one-time products, so the same tip can be given again; nothing in the app is unlocked by them.
 * All callbacks are delivered on the main thread.
 */
internal class PlayTips(
    context: Context,
    private val onOptions: (List<TipOption>) -> Unit,
    private val onEvent: (TipEvent) -> Unit,
) : PurchasesUpdatedListener {
    private val main = Handler(Looper.getMainLooper())
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingResponseCode.OK) {
                        loadOptions()
                        finishEarlierTips()
                    } else {
                        post(TipEvent.UNAVAILABLE)
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }

    fun close() = client.endConnection()

    /** Opens Play's purchase sheet for [option]. */
    fun tip(activity: Activity, option: TipOption) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(option.details).build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) post(TipEvent.FAILED)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> finishEarlierTips()
            else -> post(TipEvent.FAILED)
        }
    }

    private fun loadOptions() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(ProductType.INAPP).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, found ->
            val options = if (result.responseCode == BillingResponseCode.OK) {
                found.productDetailsList.mapNotNull { details ->
                    details.oneTimePurchaseOfferDetails?.let { offer ->
                        offer.priceAmountMicros to TipOption(details.productId, offer.formattedPrice, details)
                    }
                }.sortedBy { it.first }.map { it.second }
            } else {
                emptyList()
            }
            main.post { if (options.isEmpty()) onEvent(TipEvent.UNAVAILABLE) else onOptions(options) }
        }
    }

    private fun handle(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> consume(purchase)
            Purchase.PurchaseState.PENDING -> post(TipEvent.PENDING)
            else -> Unit
        }
    }

    /** Consuming acknowledges the tip (Play refunds unacknowledged ones after three days) and allows it again. */
    private fun consume(purchase: Purchase) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.consumeAsync(params) { result, _ ->
            if (result.responseCode == BillingResponseCode.OK) post(TipEvent.THANKS)
        }
    }

    /** Tips paid while the app was closed (or still pending last time) are finished here. */
    private fun finishEarlierTips() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) {
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach(::consume)
            }
        }
    }

    private fun post(event: TipEvent) {
        main.post { onEvent(event) }
    }

    companion object {
        /** One-time products to create in Play Console (Monetize > Products > In-app products). */
        val PRODUCT_IDS = listOf("tip_49", "tip_99", "tip_199", "tip_499")
    }
}
