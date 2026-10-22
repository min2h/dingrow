import { test, before, after } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, serverTimestamp } from "firebase/firestore";

let env: RulesTestEnvironment;
before(async () => {
  env = await initializeTestEnvironment({ projectId: "demo-dingrow", firestore: { rules: readFileSync("../firestore.rules", "utf8") } });
});
after(async () => { await env?.cleanup(); });

test("owner can write own save with server timestamp", async () => {
  const db = env.authenticatedContext("alice").firestore();
  await assertSucceeds(setDoc(doc(db, "users/alice"), { state: "{}", schema: 1, summary: {}, updatedAt: serverTimestamp() }));
  await assertSucceeds(getDoc(doc(db, "users/alice")));
});
test("client timestamps / extra fields rejected", async () => {
  const db = env.authenticatedContext("alice").firestore();
  await assertFails(setDoc(doc(db, "users/alice"), { state: "{}", updatedAt: new Date(0) }));
  await assertFails(setDoc(doc(db, "users/alice"), { state: "{}", coinsCredit: 99, updatedAt: serverTimestamp() }));
});
test("other users and guests are denied", async () => {
  await assertFails(getDoc(doc(env.authenticatedContext("bob").firestore(), "users/alice")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "users/alice")));
});
test("credits are read-only for the client", async () => {
  const db = env.authenticatedContext("alice").firestore();
  await assertFails(setDoc(doc(db, "users/alice/credits/tok"), { coins: 1000 }));
  await assertSucceeds(getDoc(doc(db, "users/alice/credits/tok")));
  await assertFails(getDoc(doc(db, "purchases/android:tok")));
});
