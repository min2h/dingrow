package app.mongle.ui

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

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

/** Which waiting gift was tapped open (in-app only; the gift itself persists in the service until kept/sold). */
internal object GiftOpen {
    var opened by mutableStateOf<String?>(null)
    var openNext by mutableStateOf(false)
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
    androidx.compose.runtime.LaunchedEffect(state.settings.theme) { ThemeState.mode = state.settings.theme }
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
    var highlightId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightId) { if (highlightId != null) { delay(4_000); highlightId = null } }
    // An empty schedule tab goes straight to the add form.
    LaunchedEffect(tab, state.schedules.isEmpty(), state.onboarded) {
        if (tab == Tab.Schedule && state.onboarded && state.schedules.isEmpty() && !showComposer) { editingId = null; showComposer = true }
    }
    var name by remember(state.onboarded) { mutableStateOf(state.petName) }

    LaunchedEffect(Unit) {
        NavigationBus.route.collect { route ->
            if (route == "add-schedule") {
                tabState.value = Tab.Schedule
                showComposer = true
                NavigationBus.consume()
            } else if (route?.startsWith("schedule/") == true) {
                tabState.value = Tab.Schedule
                showComposer = false
                overlayState.value = "none"
                highlightId = route.removePrefix("schedule/")
                NavigationBus.consume()
            } else if (route == "gift") {
                // Notification tap: go home and open the waiting gift.
                tabState.value = Tab.Home; showComposer = false; overlayState.value = "none"
                GiftOpen.openNext = true
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
        Box(Modifier.fillMaxSize().background(Cream).windowInsetsPadding(WindowInsets.safeDrawing)) {
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
        }
        return@LanguageProvider
    }

    Box(Modifier.fillMaxSize().background(Cream).windowInsetsPadding(WindowInsets.safeDrawing)) {
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
        SettingsV2(
            state = state,
            permissions = permissions,
            onReset = { scope.launch { service.resetAll(); overlayState.value = "none"; tabState.value = Tab.Home } },
            onChange = { scope.launch { service.updateSettings(it) } },
            onTip = { scope.launch { service.showTip() } },
            onPin = { onPinWidget(false) },
            onWallpaper = onOpenWallpaper,
            onShop = {
                shopPhase = "list"
                overlayState.value = "shop"
            },
            onClose = { overlayState.value = "none" },
            onExport = { scope.launch { val json = service.exportBackup(); BackupBridge.export?.invoke(json) } },
            onRestore = { BackupBridge.pickRestore?.invoke { text -> scope.launch { service.importBackup(text) } } },
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
    } else if (overlay == "itemshop") {
        ShopStudioScreen(state, service, scope, onClose = { overlayState.value = "none" }, onEarn = { overlayState.value = "none"; tabState.value = Tab.Schedule })
    } else if (overlay == "decorate") {
        DecorateScreen(state, service, scope, onClose = { overlayState.value = "none" }, onShop = { overlayState.value = "itemshop" })
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
                weather = if (tab == Tab.Home) state.weather else null,
                fahrenheit = state.settings.fahrenheit,
                onSettings = if (tab == Tab.Home) null else ({ overlayState.value = "settings" }),
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
                Tab.Home -> HomeScreen(state, scope, service, onAdd = { tabState.value = Tab.Schedule; editingId = null; showComposer = true })
                Tab.Schedule -> ScheduleScreen(
                    state,
                    scope,
                    service,
                    permissions,
                    onOpenExactAlarm,
                    onOpenSettings = { overlayState.value = "settings" },
                    onAdd = { editingId = null; showComposer = true },
                    onEdit = { editingId = it; showComposer = true },
                    highlightId = highlightId,
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
                    onItemShop = { overlayState.value = "itemshop" },
                    onDecorateScreen = { overlayState.value = "decorate" },
                    onEarn = { tabState.value = Tab.Schedule },
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
    if (state.statusMessage.isNotBlank() && !showComposer && overlay == "none") {
        Text(
            state.statusMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
                .testTag("status-toast")
                .clip(Pill)
                .background(BubbleInk)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp,
        )
    }
    }
    }
}

@Composable
private fun TopBar(title: String, coins: Int, weather: app.mongle.logic.WeatherSnapshot? = null, fahrenheit: Boolean = false, onSettings: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Cream).padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Ink)
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            WeatherChip(weather, fahrenheit)
            Spacer(Modifier.width(10.dp))
        }
        CoinPill(coins)
        if (onSettings != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(CardWhite).clickable(onClick = onSettings).testTag("open-settings"), contentAlignment = Alignment.Center) {
                MarkIcon(Mark.Gear, Modifier.size(16.dp), Ink)
            }
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
                colors = ButtonDefaults.buttonColors(containerColor = PeachDeep, contentColor = Color.White),
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = PeachDeep,
                    contentColor = Color.White,
                    disabledContainerColor = PeachDeep.copy(alpha = 0.45f),
                    disabledContentColor = Color.White,
                ),
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
    onAdd: () -> Unit = {},
) {
    HomeV4(
        state = state,
        onPet = { scope.launch { service.react(PetGesture.TAP) } },
        onRoamRest = { scope.launch { service.selfTalk() } },
        onComplete = { id -> scope.launch { service.completeSchedule(id) } },
        onAdd = onAdd,
        actions = {
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomActionButton(Mark.Bowl, t("feed"), "feed") { scope.launch { service.feed() } }
                RoomActionButton(Mark.Paw, t("outing"), "outing") { scope.launch { service.startOuting() } }
                if (state.sick) RoomActionButton(Mark.Heart, t("medicine_take"), "medicine") { scope.launch { service.takeMedicine() } }
            }
        },
        extra = {
            state.outing?.let { OutingCard(it.returnAt, it.startedAt, service::nowMillis) }
        val giftKey0 = (state.pendingGift?.acquiredAt ?: 0L).toString() + state.pendingDropKind + state.pendingDropCoins
        if (GiftOpen.openNext && (state.pendingGift != null || state.pendingDropKind == "COINS")) { GiftOpen.opened = giftKey0; GiftOpen.openNext = false }
        val giftKey = (state.pendingGift?.acquiredAt ?: 0L).toString() + state.pendingDropKind + state.pendingDropCoins
        if ((state.pendingGift != null || state.pendingDropKind == "COINS") && GiftOpen.opened != giftKey) {
                Spacer(Modifier.height(8.dp))
                GiftCarrier(state) { GiftOpen.opened = giftKey }
        } else if (state.pendingGift != null || state.pendingDropKind == "COINS") {
                Spacer(Modifier.height(8.dp))
                SoftCard(Modifier.testTag("gift-opened")) {
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
        },
    )
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
    highlightId: String? = null,
) {
    val zone = TimeZone.currentSystemDefault()
    val focusDate = highlightId?.let { h -> state.schedules.firstOrNull { it.id.substringBefore('@') == h.substringBefore('@') } }
        ?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it.timeMillis).toLocalDateTime(zone).date }
    var selected by remember(focusDate) { mutableStateOf(focusDate ?: Clock.System.now().toLocalDateTime(zone).date) }
    val dayStart = LocalDateTime(selected.year, selected.monthNumber, selected.dayOfMonth, 0, 0).toInstant(zone).toEpochMilliseconds()
    val nextDay = selected.plus(1, DateTimeUnit.DAY)
    val dayEnd = LocalDateTime(nextDay.year, nextDay.monthNumber, nextDay.dayOfMonth, 0, 0).toInstant(zone).toEpochMilliseconds()
    val cards = cardsForDay(state.schedules, dayStart, dayEnd, Clock.System.now().toEpochMilliseconds(), zone.id)
    ScheduleDayList(
        state = state, cards = cards, selected = selected, highlightId = highlightId,
        onSelectDay = { selected = it }, onAdd = onAdd,
        onComplete = { id -> scope.launch { service.completeSchedule(id) } },
        onSnooze = { id -> scope.launch { service.snooze(id.substringBefore('@')) } },
        onSkip = { id -> scope.launch { service.skipSchedule(id) } },
        onEdit = onEdit,
        onDelete = { id -> scope.launch { service.deleteSchedule(id.substringBefore('@')) } },
        banner = {
            if (!permissions.exactAlarmsOn && permissions.supportsExactAlarm) {
                TextButton(onClick = onOpenExactAlarm) { Text(t("allow_exact")) }
            }
        },
    )
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
    onItemShop: () -> Unit = {},
    onDecorateScreen: () -> Unit = {},
    onEarn: () -> Unit = {},
) {
    var roomAsk by remember { mutableStateOf<String?>(null) }
    var roomBusy by remember { mutableStateOf(false) }
    roomAsk?.let { id -> RoomBuyDialog(id, state.coins, roomBusy, onEarn, onDismiss = { roomAsk = null }) {
        roomBusy = true
        scope.launch {
            val r = service.buyRoom(id)
            if (r.ok) state.presets.firstOrNull { it.wallpaperId == id }?.let { service.selectPreset(it.index) }
            roomBusy = false; roomAsk = null
        }
    } }
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
        if (state.sick) {
            Spacer(Modifier.height(10.dp))
            MedicineCard(state.coins) { scope.launch { service.takeMedicine() } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.presets.forEach { preset ->
                val selected = preset.index == state.activePreset
                val locked = !app.mongle.logic.Shop.roomOwned(preset.wallpaperId, state.owned)
                Text(
                    L10n.preset(preset.wallpaperId) + if (locked) " 🔒 ${app.mongle.logic.Shop.roomPrice(preset.wallpaperId)}" else "",
                    modifier = Modifier
                        .clip(Pill)
                        .background(if (selected) PeachDeep else CardWhite)
                        .clickable { if (locked) roomAsk = preset.wallpaperId else scope.launch { service.selectPreset(preset.index) } }
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
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(t("deco_open"), Mark.House, MintSoft, Modifier.weight(1f).testTag("open-decorate"), onDecorateScreen)
            PillButton(t("shop_open"), Mark.Coin, PeachSoft, Modifier.weight(1f).testTag("open-shop"), onItemShop)
        }
        state.gifts.firstOrNull()?.let { gift ->
            TextButton(onClick = { onResell(gift.defId) }) { Text(tf("sell_named", gift.name)) }
        }
        TextButton(onClick = onShop) { Text(t("coin_shop")) }
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
        Modifier.height(30.dp).widthIn(min = 78.dp).clip(Pill).background(Butter).padding(horizontal = 10.dp).testTag("coin-pill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            drawCircle(Color(0xFFFFCE54)); drawCircle(Color(0xFFD6A032), style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
            drawCircle(Color(0xFFD6A032), size.minDimension * 0.22f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        }
        Spacer(Modifier.weight(1f, fill = false).widthIn(min = 8.dp))
        Text(coinsLabel(coins), color = BubbleInk, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

private fun coinsLabel(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
