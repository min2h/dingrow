package app.mongle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.logic.Korean
import app.mongle.model.Catalog
import app.mongle.model.DecorSlot
import app.mongle.model.GameState
import app.mongle.model.ItemDef
import app.mongle.model.ItemKind
import app.mongle.model.Rarity
import app.mongle.model.RepeatRule
import app.mongle.model.SpeciesCatalog
import app.mongle.platform.NavigationBus
import app.mongle.service.GameService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private enum class Tab(val label: String) {
    Home("홈"),
    Schedule("일정"),
    Room("방 꾸미기"),
    My("마이"),
}

private enum class RoomPane { Furniture, Outfit }

private val CardShape = RoundedCornerShape(24.dp)
private val Pill = RoundedCornerShape(20.dp)

@Composable
fun MongleAppScreen(
    service: GameService,
    permissions: PermissionSnapshot,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenWallpaper: () -> Unit,
) {
    val state by service.state.collectAsState()
    val scope = rememberCoroutineScope()
    val tabState = remember { mutableStateOf(Tab.Home) }
    val tab = tabState.value
    var showComposer by remember { mutableStateOf(false) }
    var name by remember(state.onboarded) { mutableStateOf(state.petName) }

    LaunchedEffect(Unit) {
        NavigationBus.route.collect { route ->
            if (route == "add-schedule") {
                tabState.value = Tab.Schedule
                showComposer = true
                NavigationBus.consume()
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            service.refresh()
            delay(30_000)
        }
    }

    if (!state.onboarded) {
        Onboarding(name, { name = it }) {
            scope.launch {
                service.completeOnboarding(name)
                onRequestNotifications()
            }
        }
        return
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopBar(
                title = when (tab) {
                    Tab.Home -> "몽글"
                    Tab.Schedule -> "일정"
                    Tab.Room -> "방 꾸미기"
                    Tab.My -> "도감"
                },
                coins = state.coins,
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp)
                    .background(CardWhite)
                    .padding(top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { item ->
                    val selected = tab == item
                    Column(
                        Modifier
                            .weight(1f)
                            .testTag("tab-${item.name}")
                            .clickable {
                                tabState.value = item
                                Snapshot.sendApplyNotifications()
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavMark(item, selected)
                        Text(
                            item.label,
                            color = if (selected) PeachDeep else InkSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Home -> HomeScreen(state, scope, service)
                Tab.Schedule -> ScheduleScreen(state, scope, service, showComposer, { showComposer = it }, permissions, onOpenExactAlarm)
                Tab.Room -> RoomScreen(state, scope, service, onOpenWallpaper)
                Tab.My -> DexScreen(state, scope, service)
            }
        }
    }
}

@Composable
private fun TopBar(title: String, coins: Int) {
    Row(
        Modifier.fillMaxWidth().background(Cream).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        CoinPill(coins)
    }
}

@Composable
private fun NavMark(tab: Tab, selected: Boolean) {
    val color = if (selected) PeachDeep else InkSoft
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (selected) PeachSoft else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (tab) {
                Tab.Home -> "●"
                Tab.Schedule -> "▢"
                Tab.Room -> "⌂"
                Tab.My -> "♥"
            },
            color = color,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Onboarding(name: String, onName: (String) -> Unit, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cream).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtImage("onboarding", Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)), "몽글")
        Spacer(Modifier.height(16.dp))
        Text("몽글", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("같이 지낼 이름을 정해 주세요", color = InkSoft)
        Spacer(Modifier.height(16.dp))
        TextField(value = name, onValueChange = onName, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            enabled = name.isNotBlank(),
            shape = Pill,
            colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("같이 시작하기") }
    }
}

@Composable
private fun HomeScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("home-scroll"),
    ) {
        RoomScene(
            state,
            Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.petName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text("Lv.${state.level}", color = PeachDeep, style = MaterialTheme.typography.titleMedium)
        }
        Text("성장 XP   ${state.xp} / ${state.xpForNext.coerceAtLeast(state.xp)}", color = InkSoft, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
            color = Butter,
            trackColor = ButterSoft,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("포만감 ${state.hunger}%", MintSoft, Modifier.weight(1f))
            StatChip("행복 ${state.happiness}%", PeachSoft, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Text("다음 일정", color = InkSoft, fontSize = 12.sp)
            val next = state.nextSchedule
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(next?.timeLabel ?: "비어 있어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(next?.title ?: "일정을 추가해 보세요", color = InkSoft, fontSize = 13.sp)
            }
            state.outing?.let { Text(it.label, color = Mint) }
        }
        if (state.statusMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(state.statusMessage, color = InkSoft, fontSize = 13.sp)
        }
        state.pendingGift?.let { gift ->
            Spacer(Modifier.height(10.dp))
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(gift.defId, Modifier.size(48.dp), gift.name)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${gift.name}이 도착했어요", style = MaterialTheme.typography.titleMedium)
                        Text("${gift.sellPrice}코인", color = InkSoft)
                    }
                }
                Row {
                    TextButton(onClick = { scope.launch { service.keepGift() } }) { Text("보관") }
                    TextButton(
                        onClick = { scope.launch { service.sellPendingOrLatest() } },
                        modifier = Modifier.testTag("sell-gift"),
                    ) { Text("팔기") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("밥주기", PeachSoft, Modifier.weight(1f).testTag("feed")) {
                scope.launch { service.feed() }
            }
            PillButton("산책", MintSoft, Modifier.weight(1f).testTag("outing")) {
                scope.launch { service.startOuting() }
            }
            PillButton("선물", ButterSoft, Modifier.weight(1f)) { }
        }
        state.outingBlockReason?.let {
            if (state.outing == null) {
                Spacer(Modifier.height(6.dp))
                Text(it, color = InkSoft, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScheduleScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    showComposer: Boolean,
    onComposer: (Boolean) -> Unit,
    permissions: PermissionSnapshot,
    onOpenExactAlarm: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            WeekStrip()
            Spacer(Modifier.height(8.dp))
            if (!permissions.exactAlarmsOn && permissions.supportsExactAlarm) {
                TextButton(onClick = onOpenExactAlarm) { Text("정확한 알람 허용") }
            }
            if (showComposer) {
                ScheduleComposer { title, time, repeat ->
                    scope.launch {
                        service.addSchedule(title, time, repeat)
                        onComposer(false)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val open = state.schedules.filter { !it.done }
            val done = state.schedules.filter { it.done }
            Text("오늘의 할 일 · ${open.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (open.isEmpty() && done.isEmpty()) {
                ArtImage("empty_schedule", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)), "빈 일정")
                Spacer(Modifier.height(8.dp))
                Text("아직 일정이 없어요", color = InkSoft)
                Spacer(Modifier.height(8.dp))
            } else if (open.isEmpty()) {
                Text("아직 일정이 없어요", color = InkSoft)
                Spacer(Modifier.height(8.dp))
            }
            open.forEach { item ->
                ScheduleCard(
                    item,
                    done = false,
                    speciesId = state.speciesId,
                    onComplete = { scope.launch { service.completeSchedule(item.id) } },
                    onSnooze = { scope.launch { service.snooze(item.id) } },
                    onDelete = { scope.launch { service.deleteSchedule(item.id) } },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("완료한 할 일 · ${done.size}", color = InkSoft)
            done.forEach { item ->
                ScheduleCard(
                    item,
                    done = true,
                    speciesId = state.speciesId,
                    onComplete = {},
                    onSnooze = {},
                    onDelete = { scope.launch { service.deleteSchedule(item.id) } },
                )
            }
            Spacer(Modifier.height(72.dp))
        }
        Button(
            onClick = { onComposer(true) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) { Text("추가") }
    }
}

@Composable
private fun WeekStrip() {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val back = (today.dayOfWeek.ordinal + 1) % 7
    val sunday = today.minus(back, DateTimeUnit.DAY)
    val labels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val date = sunday.plus(index, DateTimeUnit.DAY)
            val selected = date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = InkSoft, fontSize = 11.sp)
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) PeachDeep else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${date.dayOfMonth}", color = if (selected) Color.White else Ink, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    item: app.mongle.model.ScheduleView,
    done: Boolean,
    speciesId: String,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
) {
    SoftCard(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (done) Mint else Color.Transparent)
                    .border(2.dp, if (done) Mint else PeachSoft, CircleShape)
                    .then(if (done) Modifier else Modifier.clickable(onClick = onComplete).testTag("complete-${item.id}")),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.timeLabel, color = InkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.repeatLabel, color = PeachDeep, fontSize = 11.sp)
            }
            StillPet(
                speciesId,
                app.mongle.model.Stage.BABY,
                if (done) app.mongle.model.Pose.HAPPY else app.mongle.model.Pose.IDLE,
                Modifier.size(46.dp),
            )
        }
        if (!done) {
            Row {
                TextButton(onClick = onSnooze) { Text("10분 뒤") }
                TextButton(onClick = onDelete) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun ScheduleComposer(onSave: (String, Long, RepeatRule) -> Unit) {
    val initial = remember { nextSlot() }
    var title by remember { mutableStateOf("") }
    var dayOffset by remember { mutableIntStateOf(initial.dayOffset) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    SoftCard {
        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("할 일") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ChipRow(listOf(0 to "오늘", 1 to "내일", 2 to "모레"), dayOffset) { dayOffset = it }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            (6..22).forEach { value ->
                TextButton(onClick = { hour = value }) { Text(if (hour == value) "[$value]" else "$value") }
            }
        }
        ChipRow(listOf(0 to "00", 10 to "10", 20 to "20", 30 to "30", 40 to "40", 50 to "50"), minute) { minute = it }
        ChipRow(listOf(RepeatRule.NONE to "한 번", RepeatRule.DAILY to "매일", RepeatRule.WEEKLY to "매주"), repeat) { repeat = it }
        Button(
            onClick = { onSave(title, composeTime(dayOffset, hour, minute), repeat) },
            enabled = title.isNotBlank(),
            shape = Pill,
            colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
        ) { Text("알림 맡기기") }
    }
}

private data class Slot(val dayOffset: Int, val hour: Int, val minute: Int)

private fun nextSlot(now: kotlinx.datetime.Instant = Clock.System.now()): Slot {
    val zone = TimeZone.currentSystemDefault()
    val ldt = now.toLocalDateTime(zone)
    var day = 0
    var hour = ldt.hour
    var minute = ((ldt.minute / 10) + 1) * 10
    if (minute >= 60) {
        minute = 0
        hour += 1
    }
    if (hour < 6) {
        hour = 6
        minute = 0
    }
    if (hour > 22) {
        day = 1
        hour = 9
        minute = 0
    }
    return Slot(day, hour, minute)
}

private fun composeTime(dayOffset: Int, hour: Int, minute: Int): Long {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val date = today.plus(dayOffset, DateTimeUnit.DAY)
    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).toInstant(zone).toEpochMilliseconds()
}

@Composable
private fun RoomScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    onOpenWallpaper: () -> Unit,
) {
    var pane by remember { mutableStateOf(RoomPane.Furniture) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("room-scroll"),
    ) {
        RoomScene(state, Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.presets.forEach { preset ->
                val selected = preset.index == state.activePreset
                Text(
                    preset.name,
                    modifier = Modifier
                        .clip(Pill)
                        .background(if (selected) PeachDeep else CardWhite)
                        .clickable { scope.launch { service.selectPreset(preset.index) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (selected) Color.White else Ink,
                    fontSize = 13.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenWallpaper, modifier = Modifier.testTag("wallpaper")) { Text("배경화면") }
            Spacer(Modifier.weight(1f))
            Text(state.todayOffer.item.name, color = InkSoft, fontSize = 12.sp)
            TextButton(
                onClick = { scope.launch { service.buy(state.todayOffer.item.id) } },
                modifier = Modifier.testTag("buy-today"),
            ) { Text("${state.todayOffer.price}") }
        }
        val hat = Catalog.byId("sprout_hat")
        if (hat != null && hat.id in state.owned) {
            Button(
                onClick = { scope.launch { service.equipOutfit(hat.id) } },
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = MintSoft, contentColor = Ink),
            ) { Text(hat.name) }
        } else if (hat != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(hat.id, Modifier.size(36.dp))
                Text(hat.name)
                TextButton(
                    onClick = { scope.launch { service.buy(hat.id) } },
                    modifier = Modifier.testTag("buy-sprout_hat"),
                ) { Text("${hat.price}") }
            }
        }
        Row(Modifier.padding(vertical = 6.dp)) {
            PaneTab("가구", pane == RoomPane.Furniture) { pane = RoomPane.Furniture }
            Spacer(Modifier.width(8.dp))
            PaneTab("의상", pane == RoomPane.Outfit) { pane = RoomPane.Outfit }
        }
        val items = if (pane == RoomPane.Furniture) {
            Catalog.furniture
        } else {
            Catalog.outfits.filter { it.id != "sprout_hat" }
        }
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    ItemCard(item, item.id in state.owned, Modifier.weight(1f)) {
                        scope.launch {
                            if (item.id !in state.owned) {
                                service.buy(item.id)
                            } else if (item.kind == ItemKind.OUTFIT) {
                                service.equipOutfit(item.id)
                            } else {
                                val slot = Catalog.slotOf(item.kind)
                                if (slot != null) service.setSlot(slot, item.id)
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DexScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("친구 ${SpeciesCatalog.all.count { it.id in state.owned }} / ${SpeciesCatalog.all.size}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SpeciesCatalog.all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { species ->
                    val owned = species.id in state.owned || species.price == 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (state.speciesId == species.id) PeachSoft else CardWhite)
                            .clickable {
                                scope.launch {
                                    if (owned) service.equipSpecies(species.id) else service.buy(species.id)
                                }
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtImage(species.id, Modifier.size(52.dp), species.name)
                        Text(species.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (owned) Korean.rarityLabel(species.rarity) else "${species.price}",
                            color = InkSoft,
                            fontSize = 10.sp,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (state.gifts.isEmpty()) {
            ArtImage("empty_gifts", Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)), "선물 없음")
            Spacer(Modifier.height(8.dp))
        }
        Text("수집", style = MaterialTheme.typography.titleMedium)
        Catalog.goods.filter { it.kind != ItemKind.FOOD }.take(24).forEach { item ->
            val owned = item.id in state.owned || item.kind == ItemKind.GIFT && state.gifts.any { it.defId == item.id }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(item.id, Modifier.size(36.dp), item.name)
                Spacer(Modifier.width(8.dp))
                Text(item.name, modifier = Modifier.weight(1f))
                Text(if (owned) "보유" else if (item.price > 0) "${item.price}" else "${item.sellPrice}", color = InkSoft, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ItemCard(item: ItemDef, owned: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            ArtImage(item.id, Modifier.fillMaxWidth().aspectRatio(1.15f), item.name)
            if (item.fresh && !owned) {
                Text("NEW", color = PeachDeep, fontSize = 9.sp, modifier = Modifier.padding(2.dp))
            }
        }
        Text(item.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(if (owned) "보유" else "${item.price}", color = if (owned) Mint else InkSoft, fontSize = 11.sp)
    }
}

@Composable
private fun PaneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(Pill)
            .background(if (selected) Ink else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (selected) Color.White else Ink,
    )
}

@Composable
private fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, CardShape, clip = false)
            .clip(CardShape)
            .background(CardWhite)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun PillButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun StatChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.clip(Pill).background(color).padding(horizontal = 12.dp, vertical = 8.dp),
        color = Ink,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CoinPill(coins: Int) {
    Text(
        "● ${coinsLabel(coins)}",
        modifier = Modifier.clip(Pill).background(ButterSoft).padding(horizontal = 12.dp, vertical = 6.dp),
        color = Ink,
        fontSize = 13.sp,
    )
}

private fun coinsLabel(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            TextButton(onClick = { onSelect(value) }) {
                Text(if (value == selected) "[$label]" else label)
            }
        }
    }
}
