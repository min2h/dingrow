package app.mongle.billing

import android.app.Activity
import android.content.Context
import app.mongle.l10n.L10n
import app.mongle.logic.BillingGateway
import app.mongle.logic.StoreProducts
import app.mongle.logic.StoreReceipt
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object BillingHost {
    @Volatile
    var activity: Activity? = null
}

/**
 * 출시 빌드에서 Play Billing으로 연결한다. 디버그 빌드는 [app.mongle.logic.FakeBilling]을 쓴다.
 */
class PlayBillingGateway(context: Context) : BillingGateway {
    private val app = context.applicationContext

    override suspend fun purchase(productId: String): StoreReceipt {
        if (StoreProducts.byId(productId) == null) return StoreReceipt(productId, "", false, L10n.t("msg_unknown_product"))
        val activity = BillingHost.activity ?: return StoreReceipt(productId, "", false, L10n.t("msg_billing_screen"))
        return suspendCancellableCoroutine { cont ->
            var self: BillingClient? = null
            val client = client { result, purchases ->
                val purchase = purchases?.firstOrNull { it.products.contains(productId) }
                if (!cont.isActive) return@client
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchase != null) {
                    // Coin packs are consumables: consume so the same pack can be bought again.
                    val consume = com.android.billingclient.api.ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    self?.consumeAsync(consume) { _, _ -> self?.endConnection() }
                    cont.resume(StoreReceipt(productId, purchase.purchaseToken, true, L10n.t("msg_billing_ok")))
                } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    cont.resume(StoreReceipt(productId, "", false, L10n.t("msg_billing_cancel")))
                } else if (cont.isActive) {
                    cont.resume(StoreReceipt(productId, "", false, L10n.t("msg_billing_fail")))
                }
            }
            self = client
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        if (cont.isActive) cont.resume(StoreReceipt(productId, "", false, L10n.t("msg_billing_connect")))
                        client.endConnection()
                        return
                    }
                    val product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                    val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
                    client.queryProductDetailsAsync(params) { queryResult, detailsList ->
                        val details = detailsList.firstOrNull()
                        if (queryResult.responseCode != BillingClient.BillingResponseCode.OK || details == null) {
                            if (cont.isActive) cont.resume(StoreReceipt(productId, "", false, L10n.t("msg_billing_missing")))
                            client.endConnection()
                            return@queryProductDetailsAsync
                        }
                        launch(client, activity, details)
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            })
            cont.invokeOnCancellation { client.endConnection() }
        }
    }

    /** Localized prices for every coin pack (e.g. "₩2,900", "$1.99"), straight from Play. */
    suspend fun prices(): Map<String, String> = suspendCancellableCoroutine { cont ->
        val client = client { _, _ -> }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) { if (cont.isActive) cont.resume(emptyMap()); client.endConnection(); return }
                val list = StoreProducts.all.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(BillingClient.ProductType.INAPP).build()
                }
                client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build()) { _, details ->
                    val map = details.mapNotNull { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it } }.toMap()
                    if (cont.isActive) cont.resume(map)
                    client.endConnection()
                }
            }
            override fun onBillingServiceDisconnected() { if (cont.isActive) cont.resume(emptyMap()) }
        })
    }

    override suspend fun restore(): List<StoreReceipt> = suspendCancellableCoroutine { cont ->
        val client = client { _, _ -> }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (cont.isActive) cont.resume(emptyList())
                    client.endConnection()
                    return
                }
                val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                client.queryPurchasesAsync(params) { query, purchases ->
                    val receipts = if (query.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases.mapNotNull { purchase ->
                            val id = purchase.products.firstOrNull() ?: return@mapNotNull null
                            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@mapNotNull null
                            StoreReceipt(id, purchase.purchaseToken, true, L10n.t("msg_billing_restored"))
                        }
                    } else {
                        emptyList()
                    }
                    if (cont.isActive) cont.resume(receipts)
                    client.endConnection()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun launch(client: BillingClient, activity: Activity, details: ProductDetails) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        client.launchBillingFlow(activity, flow)
    }

    private fun client(
        listener: (BillingResult, List<Purchase>?) -> Unit,
    ): BillingClient {
        return BillingClient.newBuilder(app)
            .setListener { result, purchases -> listener(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }
}
