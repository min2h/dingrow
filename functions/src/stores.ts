import { google } from "googleapis";
import {
  AppStoreServerAPIClient, Environment, SignedDataVerifier, APIException,
} from "@apple/app-store-server-library";
import { Platform, StoreCheck, StoreVerifier } from "./core";

const PACKAGE = process.env.ANDROID_PACKAGE ?? "app.dingrow";
const BUNDLE_ID = process.env.IOS_BUNDLE_ID ?? "app.dingrow";

/** Google Play: Android Publisher API purchases.products.get, using the function's service account (granted in Play Console). */
async function checkPlay(productId: string, token: string): Promise<StoreCheck> {
  try {
    const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
    const api = google.androidpublisher({ version: "v3", auth });
    const r = await api.purchases.products.get({ packageName: PACKAGE, productId, token });
    // purchaseState: 0 purchased, 1 canceled, 2 pending
    if (r.data.purchaseState !== 0) return { ok: false, reason: `purchaseState ${r.data.purchaseState}`, retryable: r.data.purchaseState === 2 };
    return { ok: true, productId, orderId: r.data.orderId ?? undefined, testPurchase: r.data.purchaseType === 0 };
  } catch (e: any) {
    const status = e?.code ?? e?.response?.status;
    if (status === 400 || status === 404 || status === 410) return { ok: false, reason: "invalid token", retryable: false };
    return { ok: false, reason: `play api ${status ?? "error"}`, retryable: true };
  }
}

/** App Store Server API (Get Transaction Info) + JWS signature verification with Apple root certs. */
async function checkApple(productId: string, transactionId: string): Promise<StoreCheck> {
  const keyId = process.env.APPSTORE_KEY_ID, issuer = process.env.APPSTORE_ISSUER_ID, key = process.env.APPSTORE_PRIVATE_KEY;
  const roots = (process.env.APPLE_ROOT_CERTS_B64 ?? "").split(",").filter(Boolean).map((b) => Buffer.from(b, "base64"));
  if (!keyId || !issuer || !key || roots.length === 0) return { ok: false, reason: "app store not configured", retryable: true };
  const appAppleId = process.env.IOS_APP_APPLE_ID ? Number(process.env.IOS_APP_APPLE_ID) : undefined;
  for (const env of [Environment.PRODUCTION, Environment.SANDBOX]) {
    try {
      const client = new AppStoreServerAPIClient(key.replace(/\\n/g, "\n"), keyId, issuer, BUNDLE_ID, env);
      const info = await client.getTransactionInfo(transactionId);
      if (!info.signedTransactionInfo) continue;
      const verifier = new SignedDataVerifier(roots, true, env, BUNDLE_ID, env === Environment.PRODUCTION ? appAppleId : undefined);
      const tx = await verifier.verifyAndDecodeTransaction(info.signedTransactionInfo);
      if (tx.revocationDate) return { ok: false, reason: "revoked", retryable: false };
      if (tx.bundleId !== BUNDLE_ID) return { ok: false, reason: "bundle mismatch", retryable: false };
      return { ok: true, productId: tx.productId ?? "", orderId: tx.transactionId, testPurchase: env === Environment.SANDBOX };
    } catch (e) {
      if (e instanceof APIException && (e.httpStatusCode === 404 || e.httpStatusCode === 400)) continue; // try sandbox next
      return { ok: false, reason: "app store api error", retryable: true };
    }
  }
  return { ok: false, reason: "transaction not found", retryable: false };
}

export const realStores: StoreVerifier = {
  check(platform: Platform, productId: string, token: string) {
    return platform === "android" ? checkPlay(productId, token) : checkApple(productId, token);
  },
};
