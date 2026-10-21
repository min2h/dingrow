package app.mongle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mongle.logic.Korean
import app.mongle.model.Catalog
import app.mongle.model.DecorSlot
import app.mongle.model.GameState
import app.mongle.model.ItemDef
import app.mongle.model.ItemKind
import app.mongle.model.RepeatRule
import app.mongle.platform.NavigationBus
import app.mongle.service.GameService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private enum class Tab(val label: String, val mark: String) {
    Home("홈", "집"),
    Schedule("일정", "일"),
    Shop("상점", "샵"),
    Room("방", "방"),
}

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
    var tab by remember { mutableStateOf(Tab.Home) }
    var showComposer by remember { mutableStateOf(false) }
    var name by remember(state.onboarded) { mutableStateOf(state.petName) }

    LaunchedEffect(Unit) {
        NavigationBus.route.collect { route ->
            if (route == "add-schedule") {
                tab = Tab.Schedule
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
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.mark, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Home -> HomeScreen(state, permissions, scope, service, onRequestNotifications, onOpenExactAlarm)
                Tab.Schedule -> ScheduleScreen(state, showComposer, { showComposer = it }, scope, service)
                Tab.Shop -> ShopScreen(state, scope, service)
                Tab.Room -> RoomScreen(state, permissions, scope, service, onOpenWallpaper, onOpenExactAlarm)
            }
        }
    }
}

@Composable
private fun Onboarding(name: String, onName: (String) -> Unit, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Cream).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("몽글", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("할 일을 끝내면, 홈 화면의 펫이 자라요.", color = Ink)
        Spacer(Modifier.height(20.dp))
        AnimatedPet(app.mongle.model.Stage.EGG, app.mongle.model.Pose.EGG, null, Modifier.size(220.dp))
        Spacer(Modifier.height(12.dp))
        Text("알을 따뜻하게 부르려면 이름이 필요해요.", color = Ink)
        Spacer(Modifier.height(12.dp))
        TextField(value = name, onValueChange = onName, singleLine = true, label = { Text("이름") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, shape = RoundedCornerShape(18.dp)) { Text("같이 시작하기") }
    }
}

@Composable
private fun HomeScreen(
    state: GameState,
    permissions: PermissionSnapshot,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarm: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(state.petName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${state.stageLabel} · ${state.moodLabel}", color = Ink.copy(alpha = 0.75f))
            }
            CoinChip(state.coins)
        }
        Spacer(Modifier.height(12.dp))
        if (state.statusMessage.isNotBlank()) {
            Banner(state.statusMessage)
            Spacer(Modifier.height(10.dp))
        }
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                RoomScene(state, Modifier.fillMaxSize())
                AnimatedPet(
                    state.stage,
                    state.pose,
                    state.outfitId,
                    Modifier.align(Alignment.Center).size(190.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Korean.greeting(state.timeOfDay, state.petName), color = Ink.copy(alpha = 0.8f))
        Spacer(Modifier.height(14.dp))
        StatBar("포만감", state.hunger, Mint)
        StatBar("행복", state.happiness, Peach)
        StatBar("기력", state.energy, Butter)
        Text(state.progressLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        LinearProgressIndicator(
            progress = { state.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(8.dp)),
            color = Peach,
            trackColor = PeachSoft,
        )
        Spacer(Modifier.height(16.dp))
        NextScheduleCard(state)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { service.feed() } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) { Text("먹이 주기") }
            OutlinedButton(
                onClick = { scope.launch { service.startOuting() } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            ) { Text(if (state.outing != null) "외출 중" else "외출") }
        }
        state.outing?.let {
            Text(it.label, modifier = Modifier.padding(top = 8.dp))
        }
        state.outingBlockReason?.let {
            if (state.outing == null) Text(it, color = Ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
        }
        state.pendingGift?.let { gift ->
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Butter.copy(alpha = 0.55f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("${gift.name}을 가져왔어요", fontWeight = FontWeight.Bold)
                    Text("${Korean.rarityLabel(gift.rarity)} · 판매 ${gift.sellPrice}코인")
                    Row {
                        TextButton(onClick = { scope.launch { service.keepGift() } }) { Text("보관") }
                        TextButton(onClick = { scope.launch { service.sellPendingOrLatest() } }) { Text("팔기") }
                    }
                }
            }
        }
        if (!permissions.notificationsOn) {
            Spacer(Modifier.height(12.dp))
            Banner("일정 시간에 몽이가 부르려면 알림을 허용해 주세요.")
            TextButton(onClick = onRequestNotifications) { Text("알림 허용") }
        }
        if (permissions.supportsExactAlarm && !permissions.exactAlarmsOn) {
            Banner("정확한 시각이 필요하면 알람 권한을 켜 주세요. 없으면 15분 정도 늦을 수 있어요.")
            TextButton(onClick = onOpenExactAlarm) { Text("알람 권한 열기") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ScheduleScreen(
    state: GameState,
    showComposer: Boolean,
    setComposer: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("일정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { setComposer(!showComposer) }, shape = RoundedCornerShape(14.dp)) {
                Text(if (showComposer) "닫기" else "추가")
            }
        }
        if (showComposer) {
            Spacer(Modifier.height(12.dp))
            ScheduleComposer { title, time, repeat ->
                scope.launch {
                    service.addSchedule(title, time, repeat)
                    setComposer(false)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.schedules.isEmpty()) {
            Text("아직 일정이 없어요. 첫 일정을 적으면 몽이가 그 시간에 불러요.")
        }
        state.schedules.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (item.done) PeachSoft.copy(alpha = 0.4f) else CardWhite),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("${item.timeLabel} · ${item.repeatLabel}" + if (item.overdue) " · 지남" else "")
                    Row {
                        if (!item.done) {
                            TextButton(onClick = { scope.launch { service.completeSchedule(item.id) } }) { Text("완료") }
                            TextButton(onClick = { scope.launch { service.snooze(item.id) } }) { Text("10분 뒤") }
                        }
                        TextButton(onClick = { scope.launch { service.deleteSchedule(item.id) } }) { Text("삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleComposer(onSave: (String, Long, RepeatRule) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dayOffset by remember { mutableIntStateOf(0) }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(14.dp)) {
            TextField(value = title, onValueChange = title.let { { value -> title = value } }, label = { Text("할 일") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ChipRow(listOf(0 to "오늘", 1 to "내일", 2 to "모레"), dayOffset) { dayOffset = it }
            Text("시", modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                (6..22).forEach { h ->
                    TextButton(onClick = { hour = h }) { Text(if (hour == h) "[$h]" else "$h") }
                }
            }
            ChipRow(listOf(0 to "00", 10 to "10", 20 to "20", 30 to "30", 40 to "40", 50 to "50"), minute) { minute = it }
            ChipRow(
                listOf(RepeatRule.NONE to "한 번", RepeatRule.DAILY to "매일", RepeatRule.WEEKLY to "매주"),
                repeat,
            ) { repeat = it }
            Button(
                onClick = { onSave(title, composeTime(dayOffset, hour, minute), repeat) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("알림 맡기기") }
        }
    }
}

private fun composeTime(dayOffset: Int, hour: Int, minute: Int): Long {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val date = today.plus(dayOffset, DateTimeUnit.DAY)
    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute).toInstant(zone).toEpochMilliseconds()
}

@Composable
private fun ShopScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("상점", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            CoinChip(state.coins)
        }
        Spacer(Modifier.height(8.dp))
        if (state.statusMessage.isNotBlank()) Banner(state.statusMessage)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Butter.copy(alpha = 0.45f))) {
            Column(Modifier.padding(16.dp)) {
                Text("오늘의 상점", fontWeight = FontWeight.Bold)
                Text("${state.todayOffer.item.name} · ${state.todayOffer.price}코인")
                Text(state.todayOffer.item.blurb)
                Button(onClick = { scope.launch { service.buy(state.todayOffer.item.id) } }, shape = RoundedCornerShape(14.dp)) {
                    Text("사기")
                }
            }
        }
        SectionTitle("먹이")
        Catalog.foods().forEach { item ->
            val qty = state.foods.find { it.defId == item.id }?.quantity ?: 0
            ShopRow(item, "${item.price}코인 · 가진 개수 $qty", "사기") { scope.launch { service.buy(item.id) } }
        }
        SectionTitle("옷과 가구")
        (Catalog.outfits + Catalog.furniture).forEach { item ->
            val owned = item.id in state.owned
            ShopRow(item, if (owned) "가지고 있어요" else "${item.price}코인", if (owned) "보유" else "사기") {
                if (!owned) scope.launch { service.buy(item.id) }
            }
        }
        SectionTitle("선물")
        if (state.pendingGift == null && state.gifts.isEmpty()) {
            Text("외출에서 돌아오면 선물이 생겨요. 어린이가 되어야 나갈 수 있어요.")
        }
        state.pendingGift?.let { gift ->
            ShopRowSimple("${gift.name} (방금 도착)", "판매 ${gift.sellPrice}코인") {
                scope.launch { service.sellPendingOrLatest() }
            }
        }
        state.gifts.forEach { gift ->
            ShopRowSimple("${gift.name} · ${Korean.rarityLabel(gift.rarity)}", "${gift.sellPrice}코인에 팔기") {
                scope.launch { service.sellGift(gift.instanceId) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RoomScreen(
    state: GameState,
    permissions: PermissionSnapshot,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    onOpenWallpaper: () -> Unit,
    onOpenExactAlarm: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("방 꾸미기", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.activeRoom.name, color = Ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(10.dp))
        Card(shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                RoomScene(state, Modifier.fillMaxSize())
                AnimatedPet(state.stage, state.pose, state.outfitId, Modifier.align(Alignment.Center).size(150.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.presets.forEach { preset ->
                val selected = preset.index == state.activePreset
                Button(
                    onClick = { scope.launch { service.selectPreset(preset.index) } },
                    shape = RoundedCornerShape(14.dp),
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                ) { Text(preset.name) }
            }
        }
        SectionTitle("옷")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { service.equipOutfit(null) } }) { Text("맨몸") }
            Catalog.outfits.filter { it.id in state.owned }.forEach { item ->
                Button(onClick = { scope.launch { service.equipOutfit(item.id) } }, shape = RoundedCornerShape(14.dp)) {
                    Text(item.name)
                }
            }
        }
        if (Catalog.outfits.none { it.id in state.owned }) Text("상점에서 모자를 사면 여기서 입혀요.")
        SlotPicker("벽", DecorSlot.WALL, ItemKind.WALL, state, scope, service)
        SlotPicker("바닥", DecorSlot.FLOOR, ItemKind.FLOOR, state, scope, service)
        SlotPicker("침대", DecorSlot.BED, ItemKind.BED, state, scope, service)
        SlotPicker("러그", DecorSlot.RUG, ItemKind.RUG, state, scope, service)
        SlotPicker("조명", DecorSlot.LAMP, ItemKind.LAMP, state, scope, service)
        Spacer(Modifier.height(16.dp))
        Text("홈 화면", fontWeight = FontWeight.Bold)
        Text("작은 위젯은 기분과 다음 일정, 큰 위젯은 이 방을 보여 줘요. 버튼으로 먹이, 완료, 판매, 구매, 방 바꾸기를 할 수 있어요.")
        if (permissions.supportsWallpaper) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenWallpaper, shape = RoundedCornerShape(14.dp)) { Text("움직이는 배경화면") }
        }
        if (permissions.supportsExactAlarm) {
            TextButton(onClick = onOpenExactAlarm) { Text("정확한 알람 설정") }
        }
        Spacer(Modifier.height(8.dp))
        Text("커뮤니티 월드와 친구 거래는 다음 단계에서 열어요. 지금은 이 방과 홈 화면이 몽이의 전부예요.", color = Ink.copy(alpha = 0.7f))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SlotPicker(
    title: String,
    slot: DecorSlot,
    kind: ItemKind,
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
) {
    val items = Catalog.furniture.filter { it.kind == kind && it.id in state.owned }
    SectionTitle(title)
    if (items.isEmpty()) {
        Text("아직 없어요.")
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            OutlinedButton(onClick = { scope.launch { service.setSlot(slot, item.id) } }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(item.name)
            }
        }
        if (slot != DecorSlot.WALL && slot != DecorSlot.FLOOR) {
            TextButton(onClick = { scope.launch { service.setSlot(slot, null) } }) { Text("치우기") }
        }
    }
}

@Composable
private fun NextScheduleCard(state: GameState) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("다음 일정", fontWeight = FontWeight.Bold)
            val next = state.nextSchedule
            if (next == null) {
                Text("비어 있어요. 일정 탭에서 하나 맡겨 보세요.")
            } else {
                Text(next.title, style = MaterialTheme.typography.titleMedium)
                Text(next.timeLabel + if (next.overdue) " · 지금 불러요" else "")
            }
        }
    }
}

@Composable
private fun StatBar(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(52.dp))
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(8.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.25f),
        )
        Text(" $value", modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun CoinChip(coins: Int) {
    Text(
        "$coins 코인",
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Butter).padding(horizontal = 12.dp, vertical = 6.dp),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PeachSoft).padding(12.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun ShopRow(item: ItemDef, detail: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text(detail)
                if (item.blurb.isNotBlank()) Text(item.blurb, color = Ink.copy(alpha = 0.7f))
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ShopRowSimple(title: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            TextButton(onClick = { onSelect(value) }) {
                Text(if (value == selected) "● $label" else label)
            }
        }
    }
}
