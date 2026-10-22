import { test } from "node:test";
import assert from "node:assert/strict";
import { CreditDb, CreditError, StoreVerifier, creditPurchase } from "../src/core";

function memDb(): CreditDb & { docs: Map<string, Record<string, unknown>> } {
  const docs = new Map<string, Record<string, unknown>>();
  return {
    docs, now: () => 1,
    async runTransaction(fn) {
      const writes: [string, Record<string, unknown>][] = [];
      const r = await fn({ get: async (p) => docs.get(p), set: (p, d) => { writes.push([p, d]); } });
      writes.forEach(([p, d]) => docs.set(p, d));
      return r;
    },
  };
}
function store(valid: Set<string>, calls = { n: 0 }): StoreVerifier {
  return { async check(_p, productId, token) { calls.n++; return valid.has(token) ? { ok: true, productId } : { ok: false, reason: "bad", retryable: false }; } };
}

test("credits a valid purchase once", async () => {
  const db = memDb(); const calls = { n: 0 }; const s = store(new Set(["tok1"]), calls);
  const a = await creditPurchase(db, s, "u1", { platform: "android", productId: "coins_100", token: "tok1" });
  const b = await creditPurchase(db, s, "u1", { platform: "android", productId: "coins_100", token: "tok1" });
  assert.deepEqual(a, { coins: 100, duplicate: false });
  assert.deepEqual(b, { coins: 100, duplicate: true });
  assert.equal(calls.n, 1);
  assert.equal([...db.docs.keys()].filter((k) => k.startsWith("users/u1/credits/")).length, 1);
});

test("ios credit id matches the client grant key", async () => {
  const db = memDb();
  await creditPurchase(db, store(new Set(["2000001"])), "u1", { platform: "ios", productId: "coins_1000", token: "2000001" });
  assert.ok(db.docs.has("users/u1/credits/ios-2000001"));
  assert.equal(db.docs.get("users/u1/credits/ios-2000001")!.coins, 1000);
});

test("token replay on another account is refused", async () => {
  const db = memDb(); const s = store(new Set(["tok9"]));
  await creditPurchase(db, s, "u1", { platform: "android", productId: "coins_100", token: "tok9" });
  await assert.rejects(creditPurchase(db, s, "u2", { platform: "android", productId: "coins_100", token: "tok9" }),
    (e: CreditError) => e.code === "permission-denied");
});

test("invalid token / product / auth rejected without credit", async () => {
  const db = memDb(); const s = store(new Set());
  await assert.rejects(creditPurchase(db, s, "u1", { platform: "android", productId: "coins_100", token: "nope" }), (e: CreditError) => e.code === "failed-precondition");
  await assert.rejects(creditPurchase(db, s, "u1", { platform: "android", productId: "coins_9999", token: "tok1" }), (e: CreditError) => e.code === "invalid-argument");
  await assert.rejects(creditPurchase(db, s, undefined, { platform: "android", productId: "coins_100", token: "tok1" }), (e: CreditError) => e.code === "permission-denied");
  assert.equal(db.docs.size, 0);
});

test("store outage is retryable (client grants locally)", async () => {
  const db = memDb();
  const s: StoreVerifier = { async check() { return { ok: false, reason: "down", retryable: true }; } };
  await assert.rejects(creditPurchase(db, s, "u1", { platform: "android", productId: "coins_100", token: "tok1" }), (e: CreditError) => e.code === "unavailable");
});

test("product mismatch rejected", async () => {
  const db = memDb();
  const s: StoreVerifier = { async check() { return { ok: true, productId: "coins_100" }; } };
  await assert.rejects(creditPurchase(db, s, "u1", { platform: "ios", productId: "coins_1000", token: "123456" }), (e: CreditError) => e.code === "failed-precondition");
});
