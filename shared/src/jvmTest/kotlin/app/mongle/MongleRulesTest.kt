package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.data.SqlGameStore
import app.mongle.db.MongleDatabase
import app.mongle.logic.GiftRoller
import app.mongle.logic.Korean
import app.mongle.logic.PetRules
import app.mongle.logic.ScheduleRules
import app.mongle.logic.ShopRotation
import app.mongle.model.Catalog
import app.mongle.model.GameBalance
import app.mongle.model.LegacyIds
import app.mongle.model.Rarity
import app.mongle.model.RoomPreset
import app.mongle.model.RepeatRule
import app.mongle.model.SpeciesCatalog
import app.mongle.model.Stage
import app.mongle.platform.JvmNotices
import app.mongle.platform.createMemoryDriver
import app.mongle.service.EconomyAuthority
import app.mongle.service.GameService
import app.mongle.service.GrantDecision
import app.mongle.service.LocalEconomyAuthority
import app.mongle.service.TimeProvider
import app.mongle.service.toSnapshotJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class MongleRulesTest {
    private val zone = TimeZone.of("Asia/Seoul")
    private val balance = GameBalance()

    @Test
    fun hungerDecaysLazilyAndClamps() {
        val start = at(2026, 10, 21, 9, 0)
        val pet = Seed.create(start).pet.copy(stage = Stage.BABY, hunger = 80, happiness = 50, energy = 40, snapshotAt = start)
        val later = PetRules.collapse(pet, start + 3 * 3_600_000, balance)
        assertEquals(68, later.hunger)
        assertEquals(start + 3 * 3_600_000, later.snapshotAt)
        val drained = PetRules.collapse(pet.copy(hunger = 5), start + 10 * 3_600_000, balance)
        assertEquals(0, drained.hunger)
        val egg = PetRules.collapse(pet.copy(stage = Stage.EGG, hunger = 80), start + 5 * 3_600_000, balance)
        assertEquals(80, egg.hunger)
    }

    @Test
    fun decayIsCappedAtThreeDays() {
        val start = at(2026, 10, 21, 9, 0)
        val pet = Seed.create(start).pet.copy(stage = Stage.CHILD, hunger = 100, happiness = 100, snapshotAt = start)
        val later = PetRules.collapse(pet, start + 10L * 24 * 3_600_000, balance)
        val cappedLoss = ((balance.decayCapMillis * balance.hungerPerHour) / 3_600_000L).toInt()
        assertEquals((100 - cappedLoss).coerceAtLeast(0), later.hunger)
    }

    @Test
    fun energyRegeneratesOnlyWhileHome() {
        val start = at(2026, 10, 21, 9, 0)
        val home = Seed.create(start).pet.copy(stage = Stage.CHILD, energy = 20, snapshotAt = start, isOut = false)
        assertEquals(40, PetRules.collapse(home, start + 2 * 3_600_000, balance).energy)
        val away = home.copy(isOut = true, energy = 20)
        assertEquals(20, PetRules.collapse(away, start + 2 * 3_600_000, balance).energy)
    }

    @Test
    fun dailyAndWeeklyAdvanceSkipMissedSlots() {
        val monday = at(2026, 10, 19, 9, 30)
        val nextDay = ScheduleRules.step(monday, RepeatRule.DAILY, zone.id)
        assertEquals(at(2026, 10, 20, 9, 30), nextDay)
        val completedLate = at(2026, 10, 21, 11, 0)
        val skipped = ScheduleRules.advance(monday, RepeatRule.DAILY, zone.id, completedLate)
        assertEquals(at(2026, 10, 22, 9, 30), skipped)
        val nextWeek = ScheduleRules.advance(monday, RepeatRule.WEEKLY, zone.id, monday)
        assertEquals(at(2026, 10, 26, 9, 30), nextWeek)
    }

    @Test
    fun rewardShrinksForInstantEarlyAndDailyCap() {
        val policy = LocalEconomyAuthority(balance)
        val now = at(2026, 10, 21, 9, 0)
        val full = policy.grantCompletion(
            app.mongle.service.CompletionRequest(now - 3 * 60_000, now, now, Stage.BABY, 0),
        ) as GrantDecision.Allow
        assertEquals(14, full.xp)
        assertEquals(8, full.coins)
        val instant = policy.grantCompletion(
            app.mongle.service.CompletionRequest(now - 30_000, now, now, Stage.BABY, 0),
        ) as GrantDecision.Allow
        assertEquals(3, instant.xp)
        val early = policy.grantCompletion(
            app.mongle.service.CompletionRequest(now - 3 * 60_000, now + 60 * 60_000, now, Stage.BABY, 0),
        ) as GrantDecision.Allow
        assertEquals(7, early.xp)
        val capped = policy.grantCompletion(
            app.mongle.service.CompletionRequest(now - 3 * 60_000, now, now, Stage.BABY, 8),
        ) as GrantDecision.Allow
        assertTrue(capped.xp < full.xp)
        assertTrue(capped.coins < full.coins)
    }

    @Test
    fun catalogMatchesSheetAndSpeciesCount() {
        assertTrue(SpeciesCatalog.all.size >= 50)
        assertEquals(SpeciesCatalog.all.size, SpeciesCatalog.all.map { it.id }.toSet().size)
        assertTrue(Catalog.goods.size >= 50)
        assertEquals(800, Catalog.byId("cushion_bed")!!.price)
        assertEquals("쿠션 침대", Catalog.byId("cushion_bed")!!.name)
        assertEquals(350, Catalog.byId("sprout_hat")!!.price)
        assertEquals("새싹 모자", Catalog.byId("sprout_hat")!!.name)
        assertEquals(50, Catalog.byId("pebble")!!.sellPrice)
        assertEquals("조약돌", Catalog.byId("pebble")!!.name)
        assertEquals(900, Catalog.byId("piano")!!.price)
        assertTrue(Catalog.byId("hoodie")!!.fresh)
        assertTrue(Catalog.rotating().any { it.stackable })
        assertTrue(Catalog.rotating().filter { !it.stackable }.all { it.price in 1..80 })
        assertEquals("peach", SpeciesCatalog.byId("missing").id)
    }

    @Test
    fun legacyIdsMigrateExistingSaves() {
        val now = at(2026, 10, 21, 9, 0)
        val seed = Seed.create(now)
        val old = seed.copy(
            pet = seed.pet.copy(speciesId = "sp_peach", outfitId = "hat_sprout", pendingGiftDefId = "gift_shell"),
            foods = mapOf("food_basic" to 2, "cookie" to 1),
            owned = setOf("hat_sprout", "wall_night", "sp_cat"),
            presets = listOf(RoomPreset(0, "포근한 방", "wall_peach", "floor_wood", "bed_cloud", "rug_leaf", "lamp_sun")),
        )
        val next = LegacyIds.migrate(old)
        assertEquals("base", next.pet.speciesId)
        assertEquals("sprout_hat", next.pet.outfitId)
        assertEquals("seashell", next.pet.pendingGiftDefId)
        assertEquals(3, next.foods["cookie"])
        assertTrue("sprout_hat" in next.owned)
        assertTrue("cat" in next.owned)
        assertTrue("starry" in next.owned)
        assertEquals("wood", next.presets[0].wallpaperId)
        assertEquals("wood", next.presets[0].floorId)
        assertEquals("cushion_bed", next.presets[0].bedId)
        assertEquals("mint_rug", next.presets[0].rugId)
        assertEquals("floor_lamp", next.presets[0].lampId)
        assertEquals(next, LegacyIds.migrate(next))
    }

    @Test
    fun giftRollIsStableAndRareIsRarer() {
        assertEquals(GiftRoller.roll(42).id, GiftRoller.roll(42).id)
        val counts = (0 until 4000).groupingBy { GiftRoller.roll(it.toLong()).rarity }.eachCount()
        assertTrue(counts.getValue(Rarity.COMMON) > counts.getValue(Rarity.UNCOMMON))
        assertTrue(counts.getValue(Rarity.UNCOMMON) > counts.getValue(Rarity.RARE))
        assertTrue(counts.getValue(Rarity.RARE) > counts.getValue(Rarity.EPIC))
    }

    @Test
    fun shopSkipsOwnedAndStaysStableForADay() {
        val now = at(2026, 10, 21, 8, 0)
        val first = ShopRotation.today(now, zone, emptySet())
        val again = ShopRotation.today(now + 3 * 3_600_000, zone, emptySet())
        assertEquals(first.item.id, again.item.id)
        assertTrue(first.discounted)
        assertTrue(first.price < first.item.price || first.item.price <= 1)
        val owned = Catalog.rotating().filter { !it.stackable }.map { it.id }.toSet()
        val offer = ShopRotation.today(now, zone, owned)
        assertTrue(offer.item.stackable)
    }

    @Test
    fun particlesFollowBatchim() {
        assertEquals("가", Korean.iGa("몽이"))
        assertEquals("이", Korean.iGa("밥"))
        assertEquals("를", Korean.eulReul("쿠키"))
        assertEquals("을", Korean.eulReul("별사탕"))
        assertTrue(Korean.formatWhen(at(2026, 10, 21, 9, 5), zone).contains("오전"))
        assertTrue(Korean.formatWhen(at(2026, 10, 21, 15, 5), zone).contains("오후"))
    }

    @Test
    fun hatchThenGrowRespectsDayGate() = runBlocking {
        val clock = MutableClock(at(2026, 10, 21, 9, 0))
        val service = service(clock, GameBalance(minDaysChild = 2, minDaysAdult = 2))
        repeat(3) { index ->
            service.addSchedule("할 일 $index", clock.now + 3 * 60_000, RepeatRule.NONE)
            clock.now += 3 * 60_000
            service.completeNext()
        }
        assertEquals(Stage.BABY, service.state.value.stage)
        val grown = Seed.create(clock.now).copy(
            pet = service.state.value.let {
                // rebuild from store via a fresh child-ready baby
                Seed.create(clock.now).pet
            },
        )
        val babyClock = MutableClock(at(2026, 10, 21, 9, 0))
        val baby = GameService(
            MemoryGameStore(
                Seed.create(babyClock.now).copy(
                    pet = Seed.create(babyClock.now).pet.copy(
                        stage = Stage.BABY,
                        xp = 80,
                        hunger = 70,
                        happiness = 70,
                        energy = 80,
                        stageSince = babyClock.now,
                    ),
                    onboarded = true,
                ),
            ),
            babyClock,
            zone,
            GameBalance(minDaysChild = 2),
            LocalEconomyAuthority(GameBalance(minDaysChild = 2)),
        )
        baby.addSchedule("공부", babyClock.now + 5 * 60_000, RepeatRule.NONE)
        babyClock.now = at(2026, 10, 22, 9, 0)
        baby.completeNext()
        assertEquals(Stage.BABY, baby.state.value.stage)
        baby.addSchedule("운동", babyClock.now + 5 * 60_000, RepeatRule.NONE)
        babyClock.now = at(2026, 10, 23, 10, 0)
        baby.completeNext()
        assertEquals(Stage.CHILD, baby.state.value.stage)
        assertTrue(grown.pet.name.isNotBlank())
    }

    @Test
    fun feedBuySellOutingAndPreset() = runBlocking {
        JvmNotices.reset()
        val clock = MutableClock(at(2026, 10, 21, 10, 0))
        val rich = Seed.create(clock.now).copy(
            coins = 500,
            pet = Seed.create(clock.now).pet.copy(
                stage = Stage.CHILD,
                xp = 80,
                hunger = 40,
                happiness = 80,
                energy = 90,
                stageSince = clock.now - 3 * 86_400_000,
            ),
            onboarded = true,
        )
        val service = GameService(MemoryGameStore(rich), clock, zone, balance, LocalEconomyAuthority(balance))
        val before = service.state.value.hunger
        val fed = service.feed()
        assertTrue(fed.ok)
        assertTrue(service.state.value.hunger > before)
        assertEquals(balance.starterFood - 1, service.state.value.foods.first { it.defId == "cookie" }.quantity)
        val full = GameService(
            MemoryGameStore(rich.copy(pet = rich.pet.copy(hunger = 100))),
            clock,
            zone,
            balance,
        )
        assertFalse(full.feed().ok)
        val bought = service.beginBuyToday()
        assertTrue(bought.ok, bought.message)
        assertTrue(service.state.value.confirmText.isNotBlank())
        clock.now += 60_000
        val expired = service.confirmBuy()
        assertFalse(expired.ok)
        val again = service.beginBuyToday()
        assertTrue(again.ok)
        val confirmed = service.confirmBuy()
        assertTrue(confirmed.ok, confirmed.message)
        assertTrue(service.state.value.coins < 500)
        assertNull(service.state.value.let { if (it.confirmText.isBlank()) null else it.confirmText })
        val outing = service.startOuting()
        assertTrue(outing.ok, outing.message)
        assertEquals(app.mongle.model.Pose.OUT, service.state.value.pose)
        assertFalse(service.startOuting().ok)
        clock.now += balance.outingDurationMillis + 1_000
        service.refresh()
        assertTrue(service.state.value.pendingGift != null)
        assertTrue(JvmNotices.immediate.isNotEmpty())
        val coins = service.state.value.coins
        val sold = service.sellPendingOrLatest()
        assertTrue(sold.ok, sold.message)
        assertTrue(service.state.value.coins > coins)
        assertEquals(null, service.state.value.pendingGift)
        val preset = service.state.value.activePreset
        service.cyclePreset()
        assertNotEquals(preset, service.state.value.activePreset)
        service.cyclePreset()
        service.cyclePreset()
        assertEquals(preset, service.state.value.activePreset)
    }

    @Test
    fun scheduleRewardsSnoozeAndAlarmSync() = runBlocking {
        JvmNotices.reset()
        val clock = MutableClock(at(2026, 10, 21, 8, 0))
        val service = service(clock, balance)
        val added = service.addSchedule("물 마시기", clock.now + 30 * 60_000, RepeatRule.DAILY)
        assertTrue(added.ok)
        assertEquals(1, JvmNotices.syncCount)
        val syncAfterAdd = JvmNotices.syncCount
        service.refresh()
        assertEquals(syncAfterAdd, JvmNotices.syncCount)
        clock.now += 30 * 60_000
        val done = service.completeNext()
        assertTrue(done.ok, done.message)
        assertTrue(service.state.value.coins > balance.starterCoins)
        val next = service.state.value.schedules.single()
        assertFalse(next.done)
        assertEquals(at(2026, 10, 22, 8, 30), next.timeMillis)
        val again = service.completeNext()
        assertFalse(again.ok)
        service.snooze(next.id)
        assertTrue(service.state.value.nextSchedule!!.timeMillis > clock.now)
        service.addSchedule("즉시", clock.now + 60_000, RepeatRule.NONE)
        val instant = service.completeSchedule(service.state.value.schedules.first { it.title == "즉시" }.id)
        assertTrue(instant.ok)
        assertTrue(instant.message.contains("경험치"))
    }

    @Test
    fun deniedAuthorityDoesNotPay() = runBlocking {
        val clock = MutableClock(at(2026, 10, 21, 8, 0))
        val denying = object : EconomyAuthority {
            override fun grantCompletion(request: app.mongle.service.CompletionRequest) = GrantDecision.Deny("서버가 거절했어요")
            override fun grantPurchase(request: app.mongle.service.PurchaseRequest) = GrantDecision.Deny("서버가 거절했어요")
        }
        val service = GameService(MemoryGameStore(Seed.create(clock.now)), clock, zone, balance, denying)
        service.addSchedule("독서", clock.now + 5 * 60_000, RepeatRule.NONE)
        clock.now += 5 * 60_000
        val result = service.completeNext()
        assertFalse(result.ok)
        assertEquals(balance.starterCoins, service.state.value.coins)
        assertEquals(0, service.state.value.completions)
    }

    @Test
    fun sqliteRoundTripAndSnapshot() = runBlocking {
        val driver = createMemoryDriver()
        val store = SqlGameStore(MongleDatabase(driver))
        val now = at(2026, 10, 21, 12, 0)
        val original = Seed.create(now).copy(onboarded = true)
        store.save(original)
        val loaded = store.load()
        assertEquals(original.pet.name, loaded?.pet?.name)
        assertEquals(original.coins, loaded?.coins)
        assertEquals(original.presets, loaded?.presets)
        assertEquals(original.foods, loaded?.foods)
        assertEquals(original.owned, loaded?.owned)
        val service = GameService(store, MutableClock(now), zone, balance)
        service.setName("보리")
        val json = service.state.value.toSnapshotJson()
        assertTrue(json.contains("보리"))
        assertTrue(json.contains("hunger"))
        driver.close()
    }

    @Test
    fun babyCannotGoOutside() = runBlocking {
        val clock = MutableClock(at(2026, 10, 21, 12, 0))
        val service = service(clock, balance)
        val result = service.startOuting()
        assertFalse(result.ok)
        assertTrue(result.message.contains("어린이"))
    }

    private fun service(clock: MutableClock, rules: GameBalance): GameService {
        return GameService(
            MemoryGameStore(Seed.create(clock.now, rules)),
            clock,
            zone,
            rules,
            LocalEconomyAuthority(rules),
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()
    }
}

private class MutableClock(var now: Long) : TimeProvider {
    override fun now(): Long = now
}
