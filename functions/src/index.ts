import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { CreditDb, CreditError, creditPurchase } from "./core";
import { realStores } from "./stores";

initializeApp();
setGlobalOptions({ region: "asia-northeast3", maxInstances: 10 });
const db = getFirestore();

const creditDb: CreditDb = {
  now: () => FieldValue.serverTimestamp(),
  runTransaction: (fn) => db.runTransaction((t) => fn({
    get: async (p) => (await t.get(db.doc(p))).data(),
    set: (p, d) => { t.set(db.doc(p), d); },
  })),
};

/** { platform: "android"|"ios", productId, token } -> { coins }. Idempotent per token. */
export const verifyPurchase = onCall(
  { secrets: ["APPSTORE_PRIVATE_KEY"], enforceAppCheck: false },
  async (req) => {
    try {
      return await creditPurchase(creditDb, realStores, req.auth?.uid, req.data ?? {});
    } catch (e) {
      if (e instanceof CreditError) throw new HttpsError(e.code, e.message);
      throw new HttpsError("unavailable", "verify failed");
    }
  },
);

/** Deletes users/{uid} (with credits) and the Auth user. purchases/* keeps only uid-less audit info. */
export const deleteAccount = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("permission-denied", "sign-in required");
  await db.recursiveDelete(db.doc(`users/${uid}`));
  const owned = await db.collection("purchases").where("uid", "==", uid).get();
  const batch = db.batch();
  owned.docs.forEach((d) => batch.update(d.ref, { uid: FieldValue.delete(), deletedUser: true }));
  if (!owned.empty) await batch.commit();
  await getAuth().deleteUser(uid).catch(() => undefined);
  return { ok: true };
});
