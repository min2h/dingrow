package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.data.SqlGameStore
import app.mongle.db.MongleDatabase
import app.mongle.logic.DropKind
import app.mongle.logic.GiftDrops
import app.mongle.logic.GiftRoller
import app.mongle.logic.Korean
import app.mongle.logic.PetRules
import app.mongle.model.EditScope
import app.mongle.logic.RecurSpec
import app.mongle.logic.Recurrence
import app.mongle.logic.ScheduleDraft
import app.mongle.logic.ScheduleRules
import app.mongle.logic.ScheduleTemplates
import app.mongle.model.EndKind
import app.mongle.logic.ShopRotation
import app.mongle.logic.resellAmount
import app.mongle.model.Catalog
import app.mongle.model.EconomyConfig
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
import kotlinx.datetime.toLocalDateTime

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
        assertEquals(EconomyConfig.shopPrice("cushion_bed"), Catalog.byId("cushion_bed")!!.price)
        assertEquals("쿠션 침대", Catalog.byId("cushion_bed")!!.name)
        assertEquals(EconomyConfig.shopPrice("sprout_hat"), Catalog.byId("sprout_hat")!!.price)
        assertEquals("새싹 모자", Catalog.byId("sprout_hat")!!.name)
        assertTrue(Catalog.byId("sprout_hat")!!.price in 150..200)
        assertEquals(50, Catalog.byId("pebble")!!.sellPrice)
        assertEquals(50, resellAmount(Catalog.byId("pebble")!!))
        assertEquals("조약돌", Catalog.byId("pebble")!!.name)
        assertEquals(EconomyConfig.shopPrice("piano"), Catalog.byId("piano")!!.price)
        assertTrue(Catalog.byId("piano")!!.price in 150..200)
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
        val reveal = service.state.value
        assertTrue(reveal.pendingGift != null || reveal.pendingDropKind.isNotBlank())
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
        assertTrue(instant.message.contains("경험치") || instant.message.contains("XP"))
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
    fun giftDropTablePityAndShopPrices() {
        val shop = Catalog.foods() + Catalog.outfits + Catalog.furniture
        assertTrue(shop.all { it.price in EconomyConfig.shopPriceMin..EconomyConfig.shopPriceMax })
        assertEquals(70, EconomyConfig.commonResellPercent)
        assertEquals(EconomyConfig.shopPrice("sprout_hat") * 70 / 100, resellAmount(Catalog.byId("sprout_hat")!!))
        assertTrue(EconomyConfig.rareResell.values.all { it in 300..600 })
        assertTrue(EconomyConfig.legendaryResell.values.all { it in 700..3000 })
        assertTrue(EconomyConfig.legendaryResell.getValue("comet_shard") > EconomyConfig.legendaryResell.getValue("dawn_feather"))
        assertEquals(EconomyConfig.rareResell.getValue("moon_bell"), resellAmount(Catalog.byId("moon_bell")!!))
        assertEquals(EconomyConfig.legendaryResell.getValue("comet_shard"), resellAmount(Catalog.byId("comet_shard")!!))
        assertEquals(0, Catalog.byId("comet_shard")!!.price)
        assertTrue(Catalog.rotating().none { it.id in Catalog.uniqueIds })
        assertEquals(GiftDrops.roll(99, 3, 0), GiftDrops.roll(99, 3, 0))
        val rolls = (0 until 8000).map { GiftDrops.roll(it.toLong(), 1, 0) }
        val counts = rolls.groupingBy { it.kind }.eachCount()
        assertTrue(counts.getValue(DropKind.COINS) in 5600..6400)
        assertTrue(counts.getValue(DropKind.COMMON) in 1400..1900)
        assertTrue(counts.getValue(DropKind.RARE) in 280..460)
        assertTrue(counts.getValue(DropKind.LEGENDARY) in 15..80)
        assertTrue(rolls.filter { it.kind == DropKind.COINS }.all { it.coins in 10..50 })
        val higher = (0 until 400).map { GiftDrops.roll(it.toLong(), 4, 0) }.filter { it.kind == DropKind.COINS }
        assertTrue(higher.maxOf { it.coins } > 50)
        assertTrue((0 until 80).all {
            val kind = GiftDrops.roll(it.toLong(), 1, EconomyConfig.pityDryGifts).kind
            kind == DropKind.RARE || kind == DropKind.LEGENDARY
        })
        assertTrue((0 until 400).any { GiftDrops.roll(it.toLong(), 1, EconomyConfig.pityDryGifts - 1).kind == DropKind.COINS })
        val hits = (0 until 4000).count { GiftDrops.completionHit(it.toLong()) }
        assertTrue(hits in 680..960)
        assertTrue(rolls.filter { it.kind == DropKind.LEGENDARY }.all { it.sparkle && it.reactionLine.isNotBlank() })
        assertTrue(rolls.filter { it.kind == DropKind.COMMON }.all { Catalog.byId(it.defId)!!.kind != app.mongle.model.ItemKind.FOOD })
        assertTrue(rolls.filter { it.kind == DropKind.RARE }.all { it.defId in Catalog.uniqueIds })
    }

    @Test
    fun babyCannotGoOutside() = runBlocking {
        val clock = MutableClock(at(2026, 10, 21, 12, 0))
        val service = service(clock, balance)
        val result = service.startOuting()
        assertFalse(result.ok)
        assertTrue(result.message.contains("어린이") || result.message.contains("child"))
    }

    @Test
    fun recurrenceMonthEndLeapYearZonesAndEdits() = runBlocking {
        val seoul = TimeZone.of("Asia/Seoul")
        val daily = RecurSpec(RepeatRule.DAILY, anchorMillis = atZone(seoul, 2026, 10, 21, 9, 30), tz = seoul.id)
        val nextSeoul = Recurrence.stepFrom(daily.anchorMillis, daily)
        assertEquals(24 * 60 * 60 * 1000L, nextSeoul - daily.anchorMillis)
        assertEquals(9, InstantOf(nextSeoul, seoul).hour)
        assertEquals(30, InstantOf(nextSeoul, seoul).minute)

        val ny = TimeZone.of("America/New_York")
        val spring = RecurSpec(RepeatRule.DAILY, anchorMillis = atZone(ny, 2026, 3, 7, 9, 30), tz = ny.id)
        val afterSpring = Recurrence.stepFrom(spring.anchorMillis, spring)
        val springLocal = InstantOf(afterSpring, ny)
        assertEquals(8, springLocal.dayOfMonth)
        assertEquals(9, springLocal.hour)
        assertEquals(30, springLocal.minute)
        assertEquals(23 * 60 * 60 * 1000L, afterSpring - spring.anchorMillis)
        val fall = RecurSpec(RepeatRule.DAILY, anchorMillis = atZone(ny, 2026, 10, 31, 9, 30), tz = ny.id)
        val afterFall = Recurrence.stepFrom(fall.anchorMillis, fall)
        val fallLocal = InstantOf(afterFall, ny)
        assertEquals(1, fallLocal.dayOfMonth)
        assertEquals(11, fallLocal.monthNumber)
        assertEquals(9, fallLocal.hour)
        assertEquals(25 * 60 * 60 * 1000L, afterFall - fall.anchorMillis)

        val jan31 = atZone(seoul, 2026, 1, 31, 9, 30)
        val monthly = RecurSpec(RepeatRule.MONTHLY, anchorMillis = jan31, tz = seoul.id)
        val february = Recurrence.stepFrom(jan31, monthly)
        assertEquals(28, InstantOf(february, seoul).dayOfMonth)
        assertEquals(2, InstantOf(february, seoul).monthNumber)
        val march = Recurrence.stepFrom(february, monthly)
        assertEquals(31, InstantOf(march, seoul).dayOfMonth)
        assertEquals(3, InstantOf(march, seoul).monthNumber)

        val leap = atZone(seoul, 2024, 2, 29, 8, 0)
        val yearly = RecurSpec(RepeatRule.YEARLY, anchorMillis = leap, tz = seoul.id)
        val y2025 = Recurrence.stepFrom(leap, yearly)
        assertEquals(2025, InstantOf(y2025, seoul).year)
        assertEquals(28, InstantOf(y2025, seoul).dayOfMonth)
        val y2027 = atZone(seoul, 2027, 2, 28, 8, 0)
        val y2028 = Recurrence.stepFrom(y2027, yearly)
        assertEquals(2028, InstantOf(y2028, seoul).year)
        assertEquals(29, InstantOf(y2028, seoul).dayOfMonth)

        val monday = atZone(seoul, 2026, 10, 19, 9, 0)
        val wedBit = 1 shl 3
        val weekly = RecurSpec(RepeatRule.WEEKLY, weekdays = (1 shl 1) or wedBit, anchorMillis = monday, tz = seoul.id)
        val wednesday = Recurrence.stepFrom(monday, weekly)
        assertEquals(21, InstantOf(wednesday, seoul).dayOfMonth)
        val fortnight = RecurSpec(RepeatRule.WEEKLY, interval = 2, anchorMillis = monday, tz = seoul.id)
        assertEquals(atZone(seoul, 2026, 11, 2, 9, 0), Recurrence.stepFrom(monday, fortnight))
        val everyThree = RecurSpec(RepeatRule.EVERY_N_DAYS, interval = 3, anchorMillis = monday, tz = seoul.id)
        assertEquals(atZone(seoul, 2026, 10, 22, 9, 0), Recurrence.stepFrom(monday, everyThree))

        val secondWed = RecurSpec(
            RepeatRule.MONTHLY,
            monthOrdinal = 2,
            weekdays = wedBit,
            anchorMillis = atZone(seoul, 2026, 3, 1, 9, 0),
            tz = seoul.id,
        )
        val hit = Recurrence.stepFrom(atZone(seoul, 2026, 3, 1, 9, 0), secondWed)
        val hitLocal = InstantOf(hit, seoul)
        assertEquals(3, hitLocal.monthNumber)
        assertTrue(hitLocal.dayOfMonth in 8..14)
        assertEquals(kotlinx.datetime.DayOfWeek.WEDNESDAY, hitLocal.dayOfWeek)

        val counted = RecurSpec(RepeatRule.DAILY, anchorMillis = monday, endKind = EndKind.COUNT, endCount = 3, tz = seoul.id)
        val second = Recurrence.stepFrom(monday, counted)
        val third = Recurrence.stepFrom(second, counted)
        assertEquals(3, Recurrence.indexOf(third, counted))
        assertNull(Recurrence.nextOpen(third, counted, third, emptySet()))

        val legacy = ScheduleRules.advance(monday, RepeatRule.DAILY, seoul.id, atZone(seoul, 2026, 10, 21, 11, 0))
        val modern = Recurrence.nextOpen(
            monday,
            RecurSpec(RepeatRule.DAILY, anchorMillis = monday, tz = seoul.id),
            atZone(seoul, 2026, 10, 21, 11, 0),
        )
        assertEquals(legacy, modern)

        assertEquals(listOf("출근", "수업", "운동 가기", "약속", "술약속", "병원", "공부", "장보기", "약 먹기"), ScheduleTemplates.all.map { it.title })
        assertTrue(ScheduleTemplates.all.all { it.category in app.mongle.logic.Categories.all })
        assertTrue(ScheduleTemplates.all.any { it.title == "출근" && it.repeat == RepeatRule.WEEKDAYS })

        val clock = MutableClock(atZone(seoul, 2026, 10, 21, 8, 0))
        val service = service(clock, balance)
        val whenAt = atZone(seoul, 2026, 10, 21, 9, 0)
        assertTrue(service.addSchedule("물", whenAt, RepeatRule.DAILY).ok)
        val id = service.state.value.schedules.single().id
        assertTrue(service.editSchedule(id, EditScope.ONE, ScheduleDraft("바뀐 물", whenAt, RepeatRule.NONE)).ok)
        val rows = service.state.value.schedules
        assertEquals(2, rows.size)
        assertEquals(atZone(seoul, 2026, 10, 22, 9, 0), rows.first { it.title == "물" }.timeMillis)
        assertTrue(rows.any { it.title == "바뀐 물" && it.repeat == RepeatRule.NONE })

        val fresh = service(MutableClock(clock.now), balance)
        assertTrue(fresh.addSchedule("운동", whenAt, RepeatRule.DAILY).ok)
        val freshId = fresh.state.value.schedules.single().id
        assertTrue(fresh.skipSchedule(freshId).ok)
        assertEquals(atZone(seoul, 2026, 10, 22, 9, 0), fresh.state.value.schedules.single().timeMillis)
        val before = fresh.state.value.hunger
        val foods = fresh.state.value.foods.sumOf { it.quantity }
        assertTrue(fresh.acknowledge(freshId).ok)
        assertTrue(fresh.state.value.hunger > before)
        assertEquals(foods, fresh.state.value.foods.sumOf { it.quantity })
        assertFalse(fresh.state.value.schedules.single().done)
        assertTrue(
            fresh.state.value.statusMessage.contains("밥을 먹었어요") || fresh.state.value.statusMessage.contains("ate"),
        )

        val all = service(MutableClock(clock.now), balance)
        assertTrue(all.addSchedule("약", whenAt, RepeatRule.DAILY).ok)
        val allId = all.state.value.schedules.single().id
        assertTrue(all.editSchedule(allId, EditScope.ALL, ScheduleDraft("전체 약", whenAt, RepeatRule.WEEKLY)).ok)
        assertEquals(1, all.state.value.schedules.size)
        assertEquals(RepeatRule.WEEKLY, all.state.value.schedules.single().repeat)

        val follow = service(MutableClock(clock.now), balance)
        assertTrue(follow.addSchedule("분리", whenAt, RepeatRule.DAILY).ok)
        val followId = follow.state.value.schedules.single().id
        assertTrue(follow.editSchedule(followId, EditScope.FOLLOWING, ScheduleDraft("이후", whenAt, RepeatRule.WEEKDAYS)).ok)
        assertTrue(follow.state.value.schedules.any { it.title == "이후" && it.repeat == RepeatRule.WEEKDAYS })
        assertTrue(follow.state.value.schedules.any { it.endKind == EndKind.UNTIL || it.done })
    }

    private fun InstantOf(millis: Long, zone: TimeZone) =
        kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)

    private fun atZone(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()
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
