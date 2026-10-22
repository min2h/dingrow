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
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.mongle.appBrandName
import app.mongle.l10n.L10n
import app.mongle.logic.FreeDecor
import app.mongle.logic.GiftDrops
import app.mongle.logic.PetGesture
import app.mongle.logic.ScheduleDraft
import app.mongle.model.Catalog
import app.mongle.model.DecorSlot
import app.mongle.model.GameState
import app.mongle.model.ItemDef
import app.mongle.model.ItemKind
import app.mongle.model.Rarity
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

private enum class Tab(val labelKey: String) {
    Home("tab_home"),
    Schedule("tab_schedule"),
    Room("tab_room"),
    My("tab_my"),
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
    onPinWidget: (Boolean) -> Unit = {},
) {
    val state by service.state.collectAsState()
    val scope = rememberCoroutineScope()
    val tabState = remember { mutableStateOf(Tab.Home) }
    val tab = tabState.value
    var showComposer by remember { mutableStateOf(false) }
    val overlayState = remember { mutableStateOf("none") }
    val overlay = overlayState.value
    var shopPhase by remember { mutableStateOf("list") }
    var shopProduct by remember { mutableStateOf<app.mongle.logic.StoreProduct?>(null) }
    var offerOpen by remember { mutableStateOf(false) }
    var resellId by remember { mutableStateOf<String?>(null) }
    var decorate by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember(state.onboarded) { mutableStateOf(state.petName) }

    LaunchedEffect(Unit) {
        NavigationBus.route.collect { route ->
            if (route == "add-schedule") {
                tabState.value = Tab.Schedule
                showComposer = true
                NavigationBus.consume()
            } else if (route?.startsWith("alarm") == true) {
                overlayState.value = "alarm"
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

    LanguageProvider(state.settings.language) {
    if (!state.onboarded) {
        Onboarding(
            name = name,
            onName = { name = it },
            onStart = {
                scope.launch {
                    service.completeOnboarding(name)
                    onRequestNotifications()
                }
            },
            onPinWidget = { onPinWidget(false) },
            onWallpaper = onOpenWallpaper,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
    if (showComposer) {
        ScheduleForm(
            editing = editingId != null,
            onClose = {
                showComposer = false
                editingId = null
            },
            onSave = { draft, scopeName ->
                scope.launch {
                    val id = editingId
                    if (id == null) service.addDraft(draft) else service.editSchedule(id, scopeName, draft)
                    showComposer = false
                    editingId = null
                }
            },
        )
    } else if (overlay == "settings") {
        SettingsScreen(
            state = state,
            permissions = permissions,
            onChange = { scope.launch { service.updateSettings(it) } },
            onTip = { scope.launch { service.showTip() } },
            onPin = { onPinWidget(false) },
            onWallpaper = onOpenWallpaper,
            onShop = {
                shopPhase = "list"
                overlayState.value = "shop"
            },
            onClose = { overlayState.value = "none" },
        )
    } else if (overlay == "shop") {
        CoinShopScreen(
            coins = state.coins,
            phase = shopPhase,
            product = shopProduct,
            message = state.statusMessage,
            onPick = {
                shopProduct = it
                shopPhase = "confirm"
            },
            onPay = {
                val id = shopProduct?.id ?: return@CoinShopScreen
                scope.launch {
                    service.purchaseProduct(id)
                    shopPhase = "done"
                }
            },
            onRestore = { scope.launch { service.restorePurchases() } },
            onClose = { overlayState.value = "none" },
        )
    } else if (overlay == "alarm") {
        val next = state.nextSchedule
        AlarmScreen(
            timeLabel = next?.timeLabel?.substringAfterLast(" ") ?: "15:00",
            title = next?.title ?: t("walk_fallback"),
            speech = tf("speech_together", next?.title ?: t("walk_fallback")),
            onAck = { scope.launch { service.acknowledge(next?.id.orEmpty()) } },
            onSnooze = { scope.launch { next?.let { service.snooze(it.id) }; overlayState.value = "none" } },
            onDone = { scope.launch { next?.let { service.completeSchedule(it.id) }; overlayState.value = "none" } },
        )
    } else Scaffold(
        containerColor = Cream,
        topBar = {
            TopBar(
                title = when (tab) {
                    Tab.Home -> t("app_name")
                    Tab.Schedule -> t("screen_schedule")
                    Tab.Room -> t("screen_room")
                    Tab.My -> t("screen_dex")
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
                            t(item.labelKey),
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
                Tab.Schedule -> ScheduleScreen(
                    state,
                    scope,
                    service,
                    permissions,
                    onOpenExactAlarm,
                    onOpenSettings = { overlayState.value = "settings" },
                    onAdd = { editingId = null; showComposer = true },
                    onEdit = { editingId = it; showComposer = true },
                )
                Tab.Room -> RoomScreen(
                    state,
                    scope,
                    service,
                    onOpenWallpaper,
                    decorate = decorate,
                    onDecorate = { decorate = !decorate },
                    onOffer = { offerOpen = true },
                    onResell = { resellId = it },
                    onShop = { shopPhase = "list"; overlayState.value = "shop" },
                )
                Tab.My -> DexScreen(state, scope, service)
            }
        }
    }
    if (offerOpen) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            OfferSheet(
                item = state.todayOffer.item,
                price = state.todayOffer.price,
                coins = state.coins,
                onBuy = {
                    scope.launch { service.buy(state.todayOffer.item.id) }
                    offerOpen = false
                },
                onShop = {
                    offerOpen = false
                    shopPhase = "list"
                    overlayState.value = "shop"
                },
                onClose = { offerOpen = false },
            )
        }
    }
    resellId?.let { id ->
        val def = Catalog.byId(id)
        if (def != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ResellSheet(
                    item = def,
                    onSell = {
                        scope.launch { service.resellOwned(id) }
                        resellId = null
                    },
                    onClose = { resellId = null },
                )
            }
        }
    }
    if (state.statusMessage.isNotBlank()) {
        Text(
            state.statusMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
                .clip(Pill)
                .background(Ink)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp,
        )
    }
    }
    }
}

@Composable
private fun TopBar(title: String, coins: Int) {
    Row(
        Modifier.fillMaxWidth().background(Cream).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon(Mark.Paw, Modifier.size(22.dp), PeachDeep)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        CoinPill(coins)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(CardWhite),
            contentAlignment = Alignment.Center,
        ) {
            MarkIcon(Mark.Bell, Modifier.size(18.dp), Ink)
        }
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
        MarkIcon(
            when (tab) {
                Tab.Home -> Mark.House
                Tab.Schedule -> Mark.Calendar
                Tab.Room -> Mark.Bed
                Tab.My -> Mark.Person
            },
            Modifier.size(16.dp),
            color,
        )
    }
}

@Composable
private fun Onboarding(
    name: String,
    onName: (String) -> Unit,
    onStart: () -> Unit,
    onPinWidget: () -> Unit,
    onWallpaper: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(Cream).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (page == 0) {
            Spacer(Modifier.height(24.dp))
            Text(t("onb_title"), style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            ArtImage("onboarding", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)), appBrandName())
            Spacer(Modifier.height(12.dp))
            Text(t("onb_body"), color = InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { page = 1 },
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(t("onb_next")) }
        } else {
            Text(t("onb_gift_title"), style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            StillPet("base", app.mongle.model.Stage.BABY, app.mongle.model.Pose.HAPPY, Modifier.size(140.dp))
            Text(t("onb_tip"), color = InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TextField(value = name, onValueChange = onName, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                enabled = name.isNotBlank(),
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(t("onb_start")) }
            TextButton(onClick = onPinWidget) { Text(t("add_widget")) }
            TextButton(onClick = onWallpaper) { Text(t("set_wallpaper")) }
        }
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("home-scroll"),
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            RoomScene(state, Modifier.fillMaxSize(), onPet = { scope.launch { service.react(PetGesture.TAP) } })
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.petName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text("Lv.${state.level}", color = PeachDeep, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            MarkIcon(Mark.Paw, Modifier.size(16.dp), PeachDeep)
        }
        Text(tf("stat_xp", state.xp, state.xpForNext.coerceAtLeast(state.xp)), color = InkSoft, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
            color = Butter,
            trackColor = ButterSoft,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(tf("stat_hunger", state.hunger), Mark.Leaf, MintSoft, Modifier.weight(1f))
            StatChip(tf("stat_happy", state.happiness), Mark.Heart, PeachSoft, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(Mark.Calendar, Modifier.size(14.dp), PeachDeep)
                Spacer(Modifier.width(6.dp))
                Text(t("next_task"), color = InkSoft, fontSize = 12.sp)
            }
            val next = state.nextSchedule
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(Mark.Paw, Modifier.size(16.dp), PeachDeep)
                Spacer(Modifier.width(6.dp))
                Text(next?.timeLabel ?: t("empty_time"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(next?.title ?: t("empty_hint"), color = InkSoft, fontSize = 13.sp)
            }
            state.outing?.let { Text(it.label, color = Mint) }
            if (state.revealKind.isNotBlank() && state.pendingGift == null && state.pendingDropKind.isBlank()) {
                Text(
                    buildString {
                        append(GiftDrops.label(state.revealKind))
                        append(" · ")
                        append(state.revealLabel)
                        if (state.revealSparkle) append(" · " + t("sparkle"))
                    },
                    color = InkSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.pendingGift != null || state.pendingDropKind == "COINS") {
            Spacer(Modifier.height(8.dp))
            SoftCard {
                val gift = state.pendingGift
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (gift != null) {
                        ArtImage(Catalog.byId(gift.defId)?.pictureId() ?: gift.defId, Modifier.size(48.dp), gift.name)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (gift != null) tf("gift_arrived_named", gift.name) else tf("coins_arrived", state.pendingDropCoins),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            buildString {
                                append(GiftDrops.label(state.pendingDropKind).ifBlank { t("gift") })
                                if (gift != null) append(" · " + tf("coins_amount", gift.sellPrice))
                                if (state.pendingSparkle) append(" · " + t("sparkle"))
                            },
                            color = InkSoft,
                        )
                    }
                }
                Row {
                    TextButton(onClick = { scope.launch { service.keepGift() } }) { Text(t("keep")) }
                    TextButton(
                        onClick = { scope.launch { service.sellPendingOrLatest() } },
                        modifier = Modifier.testTag("sell-gift"),
                    ) { Text(if (state.pendingDropKind == "COINS") t("claim") else t("sell_short")) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(t("feed"), Mark.Bowl, PeachSoft, Modifier.weight(1f).testTag("feed")) {
                scope.launch { service.feed() }
            }
            PillButton(t("outing"), Mark.Paw, MintSoft, Modifier.weight(1f).testTag("outing")) {
                scope.launch { service.startOuting() }
            }
            PillButton(t("gift"), Mark.Gift, ButterSoft, Modifier.weight(1f)) { }
        }
        state.outingBlockReason?.let {
            if (state.outing == null) {
                Spacer(Modifier.height(4.dp))
                Text(it, color = InkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ScheduleScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    permissions: PermissionSnapshot,
    onOpenExactAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    var selected by remember { mutableStateOf(Clock.System.now().toLocalDateTime(zone).date) }
    val dayStart = LocalDateTime(selected.year, selected.monthNumber, selected.dayOfMonth, 0, 0).toInstant(zone).toEpochMilliseconds()
    val nextDay = selected.plus(1, DateTimeUnit.DAY)
    val dayEnd = LocalDateTime(nextDay.year, nextDay.monthNumber, nextDay.dayOfMonth, 0, 0).toInstant(zone).toEpochMilliseconds()
    val cards = cardsForDay(state.schedules, dayStart, dayEnd, Clock.System.now().toEpochMilliseconds(), zone.id)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekStrip(selected, Modifier.weight(1f)) { selected = it }
                TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("open-settings")) { Text(t("settings")) }
            }
            if (!permissions.exactAlarmsOn && permissions.supportsExactAlarm) {
                TextButton(onClick = onOpenExactAlarm) { Text(t("allow_exact")) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkIcon(Mark.Calendar, Modifier.size(18.dp), PeachDeep)
                    Spacer(Modifier.width(6.dp))
                    Text(tf("today_tasks", cards.count { !it.done }), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (cards.isEmpty()) {
                    ArtImage("empty_schedule", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)), t("empty_schedule_cd"))
                    Spacer(Modifier.height(8.dp))
                    Text(t("no_tasks_yet"), color = InkSoft)
                }
                cards.forEach { card ->
                    ScheduleCard(
                        card.view,
                        done = card.done,
                        speciesId = state.speciesId,
                        onComplete = { scope.launch { service.completeSchedule(card.view.id) } },
                        onSnooze = { scope.launch { service.snooze(card.view.id.substringBefore('@')) } },
                        onDelete = { scope.launch { service.deleteSchedule(card.view.id.substringBefore('@')) } },
                        onSkip = { scope.launch { service.skipSchedule(card.view.id) } },
                        onEdit = { onEdit(card.view.id) },
                    )
                }
                Spacer(Modifier.height(72.dp))
            }
        }
        Button(
            onClick = onAdd,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) { Text(t("add")) }
    }
}

@Composable
private fun WeekStrip(
    selectedDate: kotlinx.datetime.LocalDate,
    modifier: Modifier = Modifier,
    onSelect: (kotlinx.datetime.LocalDate) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val back = (today.dayOfWeek.ordinal + 1) % 7
    val sunday = today.minus(back, DateTimeUnit.DAY)
    val labels = L10n.weekLetters()
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            val date = sunday.plus(index, DateTimeUnit.DAY)
            val selected = date == selectedDate
            Column(Modifier.clickable { onSelect(date) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = InkSoft, fontSize = 11.sp)
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (selected) PeachDeep else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${date.dayOfMonth}", color = if (selected) Color.White else Ink, fontSize = 12.sp, lineHeight = 12.sp)
                        if (selected) MarkIcon(Mark.Paw, Modifier.size(10.dp), Color.White)
                    }
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
    onSkip: () -> Unit = {},
    onEdit: () -> Unit = {},
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
                contentAlignment = Alignment.Center,
            ) {
                if (done) MarkIcon(Mark.Paw, Modifier.size(12.dp), CardWhite)
            }
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
                TextButton(onClick = onSnooze) { Text(t("snooze_5")) }
                TextButton(onClick = onSkip) { Text(t("skip")) }
                TextButton(onClick = onEdit) { Text(t("edit")) }
                TextButton(onClick = onDelete) { Text(t("delete")) }
            }
        }
    }
}

@Composable
private fun RoomScreen(
    state: GameState,
    scope: kotlinx.coroutines.CoroutineScope,
    service: GameService,
    onOpenWallpaper: () -> Unit,
    decorate: Boolean,
    onDecorate: () -> Unit,
    onOffer: () -> Unit,
    onResell: (String) -> Unit,
    onShop: () -> Unit,
) {
    var pane by remember { mutableStateOf(RoomPane.Furniture) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("room-scroll"),
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            RoomScene(state, Modifier.fillMaxHeight())
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.presets.forEach { preset ->
                val selected = preset.index == state.activePreset
                Text(
                    L10n.preset(preset.wallpaperId),
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
            TextButton(onClick = onOpenWallpaper, modifier = Modifier.testTag("wallpaper")) {
                MarkIcon(Mark.House, Modifier.size(16.dp), Ink)
                Spacer(Modifier.width(4.dp))
                Text(t("wallpaper_change"))
            }
            TextButton(onClick = onDecorate) { Text(if (decorate) t("done_place") else t("place")) }
            Text(
                L10n.item(state.todayOffer.item.id),
                color = InkSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOffer, modifier = Modifier.testTag("offer-detail")) { Text(t("view")) }
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
            ) { Text(L10n.item(hat.id)) }
        } else if (hat != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(hat.id, Modifier.size(36.dp))
                Text(L10n.item(hat.id))
                TextButton(
                    onClick = { scope.launch { service.buy(hat.id) } },
                    modifier = Modifier.testTag("buy-sprout_hat"),
                ) { Text("${hat.price}") }
            }
        }
        if (decorate) {
            Text(t("free_place_hint"), color = InkSoft, fontSize = 12.sp)
            TextButton(onClick = onShop) { Text(t("coin_shop")) }
            state.gifts.firstOrNull()?.let { gift ->
                TextButton(onClick = { onResell(gift.defId) }) { Text(tf("sell_named", gift.name)) }
            }
            state.placements.forEach { placement ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.item(placement.defId), modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { service.rotatePlacement(placement.defId) } }) { Text(t("rotate")) }
                    TextButton(onClick = { scope.launch { service.removePlacement(placement.defId) } }) { Text(t("delete")) }
                }
            }
        }
        Row(Modifier.padding(vertical = 6.dp)) {
            PaneTab(t("pane_furniture"), Mark.Bed, pane == RoomPane.Furniture) { pane = RoomPane.Furniture }
            Spacer(Modifier.width(8.dp))
            PaneTab(t("pane_outfit"), Mark.Hanger, pane == RoomPane.Outfit) { pane = RoomPane.Outfit }
        }
        val items = if (pane == RoomPane.Furniture) {
            Catalog.furniture
        } else {
            Catalog.outfits.filter { it.id != "sprout_hat" }
        }
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    ItemCard(item, item.id in state.owned || (decorate && FreeDecor.allowed(item.id, state.owned)), Modifier.weight(1f)) {
                        scope.launch {
                            if (decorate && FreeDecor.allowed(item.id, state.owned)) {
                                service.placeDecor(item.id)
                            } else if (item.id !in state.owned) {
                                service.buy(item.id)
                            } else if (item.kind == ItemKind.OUTFIT) {
                                service.equipOutfit(item.id)
                            } else if (decorate) {
                                service.placeDecor(item.id)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkIcon(Mark.Paw, Modifier.size(18.dp), PeachDeep)
            Spacer(Modifier.width(6.dp))
            Text(
                tf("friends_count", SpeciesCatalog.all.count { it.id in state.owned }, SpeciesCatalog.all.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
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
                        ArtImage(species.id, Modifier.size(52.dp), L10n.species(species.id))
                        Text(L10n.species(species.id), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (owned) L10n.t("rarity_${species.rarity.name.lowercase()}") else "${species.price}",
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
            ArtImage("empty_gifts", Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)), t("empty_gifts_cd"))
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkIcon(Mark.Gift, Modifier.size(18.dp), PeachDeep)
            Spacer(Modifier.width(6.dp))
            Text(t("collection"), style = MaterialTheme.typography.titleMedium)
        }
        Catalog.goods.filter { it.kind != ItemKind.FOOD }.take(24).forEach { item ->
            val owned = item.id in state.owned || item.kind == ItemKind.GIFT && state.gifts.any { it.defId == item.id }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(item.id, Modifier.size(36.dp), L10n.item(item.id))
                Spacer(Modifier.width(8.dp))
                Text(L10n.item(item.id), modifier = Modifier.weight(1f))
                Text(if (owned) t("owned_short") else if (item.price > 0) "${item.price}" else "${item.sellPrice}", color = InkSoft, fontSize = 12.sp)
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
            ArtImage(item.id, Modifier.fillMaxWidth().aspectRatio(1.15f), L10n.item(item.id))
            if (item.fresh && !owned) {
                Text("NEW", color = PeachDeep, fontSize = 9.sp, modifier = Modifier.padding(2.dp))
            }
        }
        Text(L10n.item(item.id), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!owned) {
                MarkIcon(Mark.Coin, Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(if (owned) t("owned") else "${item.price}", color = if (owned) Mint else InkSoft, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaneTab(label: String, mark: Mark, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Pill)
            .background(if (selected) Ink else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon(mark, Modifier.size(16.dp), if (selected) Color.White else Ink)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else Ink)
    }
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
private fun PillButton(label: String, mark: Mark, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MarkIcon(mark, Modifier.size(18.dp), Ink)
            Text(label, maxLines = 1, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatChip(label: String, mark: Mark, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Pill).background(color).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MarkIcon(mark, Modifier.size(14.dp), Ink)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Ink, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun CoinPill(coins: Int) {
    Row(
        Modifier.clip(Pill).background(ButterSoft).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon(Mark.Coin, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(coinsLabel(coins), color = Ink, fontSize = 13.sp)
    }
}

private fun coinsLabel(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
