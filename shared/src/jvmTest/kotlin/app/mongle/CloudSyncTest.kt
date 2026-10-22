package app.mongle

import app.mongle.cloud.CloudMerge
import app.mongle.cloud.CloudSync
import app.mongle.cloud.FakeCloudBackend
import app.mongle.cloud.ServerCredit
import app.mongle.cloud.SyncStatus
import app.mongle.cloud.VerifyResult
import app.mongle.data.MemoryGameStore
import app.mongle.logic.FakeBilling
import app.mongle.model.LedgerEntry
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncTest {
    private class Clock(var now: Long) : TimeProvider { override fun now() = now }
    private val t0 = 1_760_000_000_000L

    private fun device(backend: FakeCloudBackend, clock: Clock = Clock(t0), billing: FakeBilling = FakeBilling()): Triple<GameService, CloudSync, FakeBilling> {
        val svc = GameService(MemoryGameStore(), time = clock, billing = billing)
        return Triple(svc, CloudSync(backend, svc), billing)
    }

    @Test fun unconfiguredBackendIsOffAndLocalStillWorks() = runBlocking {
        val (svc, sync) = device(FakeCloudBackend(available = false))
        assertEquals(SyncStatus.OFF, sync.syncNow())
        svc.debugAddCoins(5)
        assertTrue(sync.deleteAccount()) // nothing on a server to delete
    }

    @Test fun firstSyncUploadsAndSecondDeviceRestoresBySigningIn() = runBlocking {
        val server = FakeCloudBackend()
        val (a, syncA) = device(server)
        a.debugAddCoins(70); a.debugGrantItem("mint_rug"); a.setName("보리")
        assertTrue(syncA.link("google", "sub-1"))
        val coins = a.state.value.coins
        // New phone / reinstall: anonymous guest first, then sign in with the same Google account.
        server.uid = null
        val (b, syncB) = device(server, Clock(t0 + 86_400_000))
        b.debugAddCoins(3) // fresh-install noise must not overwrite or add to the account
        assertTrue(syncB.link("google", "sub-1"))
        assertEquals(coins, b.state.value.coins)
        assertTrue("mint_rug" in b.snapshot().owned)
        assertEquals("보리", b.snapshot().pet.name)
    }

    @Test fun offlineEarningsOnTwoDevicesAreAddedOnceEach() = runBlocking {
        val server = FakeCloudBackend()
        val clock = Clock(t0)
        val (a, syncA) = device(server, clock)
        syncA.syncNow()
        val uid = server.uid!!
        val (b, syncB) = device(server, clock)
        syncB.syncNow() // same uid -> restores A
        val base = b.state.value.coins
        clock.now += 60_000; a.debugAddCoins(10)
        clock.now += 60_000; b.debugAddCoins(25)
        syncA.syncNow(); syncB.syncNow(); syncA.syncNow()
        assertEquals(base + 35, a.state.value.coins)
        assertEquals(base + 35, b.state.value.coins)
        repeat(3) { syncA.syncNow(); syncB.syncNow() } // repeated syncs never re-add
        assertEquals(base + 35, b.state.value.coins)
        assertEquals(uid, server.uid)
    }

    @Test fun mergeReplaysLedgerByIdAndKeyAndUnionsOwned() {
        runBlocking {
            val (svc) = device(FakeCloudBackend())
            val base0 = svc.snapshot()
            val base = base0.copy(extras = base0.extras + (CloudMerge.LAST_SYNC to "100") + (CloudMerge.CHANGED_AT to "100"))
            val iap = LedgerEntry("x1", 100, "IAP", "coins_100", 150, "iap:T1")
            val local = base.copy(coins = base.coins + 100 + 5, iapTokens = setOf("T1"), owned = base.owned + "star_lamp", ownedAt = base.ownedAt + ("star_lamp" to 160L),
                ledger = base.ledger + iap + LedgerEntry("x2", 5, "COMPLETE", "s", 170, null),
                extras = base.extras + (CloudMerge.CHANGED_AT to "170"))
            // Server already got the same purchase credited by the function under another entry id.
            val remote = base.copy(coins = base.coins + 100, iapTokens = setOf("T1"),
                ledger = base.ledger + LedgerEntry("srv-T1", 100, "IAP", "coins_100", 140, "iap:T1"))
            val m = CloudMerge.merge(local, remote, remoteUpdatedAt = 200)
            assertEquals(base.coins + 105, m.coins)
            assertTrue("star_lamp" in m.owned)
            assertEquals(1, m.ledger.count { it.idempotencyKey == "iap:T1" })
        }
    }

    @Test fun serverCreditsAreAppliedExactlyOnce() = runBlocking {
        val (svc) = device(FakeCloudBackend())
        val s0 = svc.snapshot()
        val credit = ServerCredit("TOK", "coins_1000", 1000, createdAt = 5)
        val once = CloudMerge.applyCredits(s0, listOf(credit, credit), 10)
        val twice = CloudMerge.applyCredits(once, listOf(credit), 11)
        assertEquals(s0.coins + 1000, twice.coins)
        assertEquals(1, twice.ledger.count { it.idempotencyKey == "iap:TOK" })
    }

    @Test fun verifiedPurchaseCreditsOnceAcrossServerAndLocalGrant() = runBlocking {
        val server = FakeCloudBackend()
        val (svc, sync, billing) = device(server)
        svc.purchaseVerifier = { p, t -> sync.verify("android", p, t) }
        sync.syncNow()
        val before = svc.state.value.coins
        assertTrue(svc.purchaseProduct("coins_100").ok)
        assertEquals(1, server.credits[server.uid]!!.size)
        sync.syncNow(); sync.syncNow()
        svc.restorePurchases(); svc.claimPendingPurchases()
        assertEquals(before + 100, svc.state.value.coins)
        // The function itself is idempotent per token.
        val token = billing.finished.single()
        assertTrue(server.verifyPurchase("android", "coins_100", token) is VerifyResult.Verified)
        assertEquals(1, server.credits[server.uid]!!.size)
    }

    @Test fun functionsUnavailableFallsBackToLocalGrant() = runBlocking {
        val server = FakeCloudBackend().apply { functionsDeployed = false }
        val (svc, sync) = device(server)
        svc.purchaseVerifier = { p, t -> sync.verify("android", p, t) }
        val before = svc.state.value.coins
        assertTrue(svc.purchaseProduct("coins_100").ok)
        assertEquals(before + 100, svc.state.value.coins)
    }

    @Test fun rejectedPurchaseIsNotGrantedOrConsumed() = runBlocking {
        val server = FakeCloudBackend().apply { validTokens = { false } }
        val (svc, sync, billing) = device(server)
        svc.purchaseVerifier = { p, t -> sync.verify("android", p, t) }
        val before = svc.state.value.coins
        assertFalse(svc.purchaseProduct("coins_100").ok)
        assertEquals(before, svc.state.value.coins)
        assertTrue(billing.finished.isEmpty())
    }

    @Test fun offlineKeepsLocalAndPushesLater() = runBlocking {
        val server = FakeCloudBackend()
        val (svc, sync) = device(server)
        sync.syncNow()
        server.offline = true
        svc.debugAddCoins(9)
        assertEquals(SyncStatus.OFFLINE, sync.syncNow())
        server.offline = false
        assertEquals(SyncStatus.OK, sync.syncNow())
        assertTrue(server.docs[server.uid]!!.state.contains("\"coins\":${svc.state.value.coins}"))
    }

    @Test fun changesNotifyAndDeleteRemovesServerData() = runBlocking {
        val server = FakeCloudBackend()
        val (svc, sync) = device(server)
        var pings = 0
        svc.onChanged = { pings++ }
        sync.syncNow()
        val uid = server.uid!!
        svc.debugAddCoins(1)
        assertTrue(pings >= 1)
        assertTrue(sync.deleteAccount())
        assertNull(server.docs[uid])
        assertNull(server.uid)
    }
}
