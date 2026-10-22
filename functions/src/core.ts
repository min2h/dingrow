/** Pure, testable purchase-credit logic (no Firebase imports). */

export type Platform = "android" | "ios";

export const PRODUCTS: Record<string, { coins: number }> = {
  coins_100: { coins: 100 },
  coins_1000: { coins: 1000 },
};

/** Store verdict for one purchase. */
export type StoreCheck =
  | { ok: true; productId: string; orderId?: string; testPurchase?: boolean }
  | { ok: false; reason: string; retryable: boolean };

export interface StoreVerifier {
  check(platform: Platform, productId: string, token: string): Promise<StoreCheck>;
}

/** Minimal transactional store the credit logic needs (Firestore in prod, a Map in tests). */
export interface CreditTx {
  get(path: string): Promise<Record<string, unknown> | undefined>;
  set(path: string, data: Record<string, unknown>): void;
}
export interface CreditDb {
  runTransaction<T>(fn: (tx: CreditTx) => Promise<T>): Promise<T>;
  now(): unknown;
}

export class CreditError extends Error {
  constructor(public code: "invalid-argument" | "permission-denied" | "failed-precondition" | "unavailable", msg: string) {
    super(msg);
  }
}

/** Client-side token key: Android uses the Play token as-is; iOS grants under "ios-<transactionId>". */
export function creditId(platform: Platform, token: string): string {
  return platform === "ios" ? `ios-${token}` : token;
}

/** Firestore doc ids can't contain "/"; Play tokens are URL-safe but be defensive. */
export function safeId(s: string): string {
  return s.replace(/\//g, "_").slice(0, 1400);
}

/**
 * Verifies with the store, then credits exactly once:
 *  - purchases/{platform:token} binds a token to the first uid that claimed it (replays on another uid are refused)
 *  - users/{uid}/credits/{creditId} is the per-user credit the app merges (key "iap:<creditId>", same as the local grant)
 * Calling again for the same uid+token returns the same result without a second credit.
 */
export async function creditPurchase(
  db: CreditDb, store: StoreVerifier, uid: string | undefined,
  input: { platform?: unknown; productId?: unknown; token?: unknown },
): Promise<{ coins: number; duplicate: boolean }> {
  if (!uid) throw new CreditError("permission-denied", "sign-in required");
  const platform = input.platform;
  const productId = input.productId;
  const token = input.token;
  if (platform !== "android" && platform !== "ios") throw new CreditError("invalid-argument", "platform");
  if (typeof productId !== "string" || !PRODUCTS[productId]) throw new CreditError("invalid-argument", "productId");
  if (typeof token !== "string" || token.length < 4 || token.length > 4096) throw new CreditError("invalid-argument", "token");

  const cid = safeId(creditId(platform, token));
  const purchasePath = `purchases/${safeId(`${platform}:${token}`)}`;
  const creditPath = `users/${uid}/credits/${cid}`;

  // Fast path: already credited to this user (no store call needed).
  const pre = await db.runTransaction(async (tx) => tx.get(purchasePath));
  if (pre) {
    if (pre.uid !== uid) throw new CreditError("permission-denied", "token already used");
    return { coins: Number(pre.coins) || 0, duplicate: true };
  }

  const verdict = await store.check(platform, productId, token);
  if (!verdict.ok) {
    throw new CreditError(verdict.retryable ? "unavailable" : "failed-precondition", verdict.reason);
  }
  if (verdict.productId !== productId) throw new CreditError("failed-precondition", "product mismatch");
  const coins = PRODUCTS[productId].coins;

  return db.runTransaction(async (tx) => {
    const existing = await tx.get(purchasePath);
    if (existing) {
      if (existing.uid !== uid) throw new CreditError("permission-denied", "token already used");
      return { coins: Number(existing.coins) || 0, duplicate: true };
    }
    tx.set(purchasePath, { uid, platform, productId, coins, orderId: verdict.orderId ?? null, test: !!verdict.testPurchase, createdAt: db.now() });
    tx.set(creditPath, { productId, coins, premium: false, legendary: false, platform, createdAt: db.now() });
    return { coins, duplicate: false };
  });
}
