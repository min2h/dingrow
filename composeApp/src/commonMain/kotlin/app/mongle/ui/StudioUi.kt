package app.mongle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.l10n.L10n
import app.mongle.logic.ConditionRules
import app.mongle.logic.Layout
import app.mongle.logic.LayoutEditor
import app.mongle.logic.Shop
import app.mongle.logic.layoutOf
import app.mongle.model.Catalog
import app.mongle.model.GameState
import app.mongle.model.ItemDef
import app.mongle.model.ItemKind
import app.mongle.model.TimeOfDay
import app.mongle.model.roomTheme
import app.mongle.service.GameService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val StudioPill = RoundedCornerShape(20.dp)
private val StudioCard = RoundedCornerShape(20.dp)

/** Items drawn on top of a room at their saved % positions (also used by the real room). */
@Composable
internal fun PlacedItems(
    layout: Layout,
    selected: String? = null,
    editable: Boolean = false,
    onSelect: (String) -> Unit = {},
    onMove: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { box = it }) {
        val itemW = maxWidth * 0.24f
        layout.placements.forEach { p ->
            key(p.defId) {
                var drag by remember(p.x, p.y) { mutableStateOf(Offset.Zero) }
                val flipped = p.defId in layout.flipped
                Box(
                    Modifier
                        .offset(x = maxWidth * (p.x / 100f) - itemW / 2, y = maxHeight * (p.y / 100f) - itemW / 2)
                        .size(itemW)
                        .graphicsLayer {
                            translationX = drag.x; translationY = drag.y
                            rotationZ = p.rotation.toFloat()
                            scaleX = if (flipped) -1f else 1f
                            val lift = if (drag != Offset.Zero) 1.08f else 1f
                            scaleX *= lift; scaleY = lift
                        }
                        .then(if (selected == p.defId) Modifier.border(2.dp, PeachDeep, RoundedCornerShape(12.dp)) else Modifier)
                        .testTag("placed-${p.defId}")
                        .then(
                            if (editable) Modifier
                                .clickable { onSelect(p.defId) }
                                .pointerInput(p.defId, p.x, p.y) {
                                    detectDragGestures(
                                        onDragStart = { onSelect(p.defId); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragEnd = {
                                            if (box.width > 0) {
                                                val nx = p.x + (drag.x / box.width * 100).toInt()
                                                val ny = p.y + (drag.y / box.height * 100).toInt()
                                                onMove(p.defId, nx, ny)
                                            }
                                            drag = Offset.Zero
                                        },
                                        onDragCancel = { drag = Offset.Zero },
                                    ) { change, amount -> change.consume(); drag += amount }
                                }
                            else Modifier,
                        ),
                ) { ArtImage(p.defId, Modifier.fillMaxSize(), L10n.item(p.defId)) }
            }
        }
    }
}

/** A room drawn from a [Layout] (preview or editing), separate from the real room. */
@Composable
internal fun RoomStage(
    state: GameState,
    layout: Layout,
    modifier: Modifier = Modifier,
    selected: String? = null,
    editable: Boolean = false,
    onSelect: (String) -> Unit = {},
    onMove: (String, Int, Int) -> Unit = { _, _, _ -> },
) {
    val night = state.timeOfDay == TimeOfDay.NIGHT
    Box(modifier.aspectRatio(3f / 4f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(28.dp))) {
        androidx.compose.foundation.Image(
            painterResource(Art.room(roomTheme(layout.roomId), night, square = false)), null,
            Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        PlacedItems(layout, selected, editable, onSelect, onMove)
        PetFigure(
            speciesId = state.speciesId, stage = state.stage, act0 = PetAct.IDLE, outfitId = layout.outfitId,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth(0.28f).aspectRatio(1f),
            animate = false, breath = 0.35f, walk = 0.2f,
        )
    }
}

private fun realLayout(state: GameState) = layoutOf(state.placements, state.outfitId, state.activeRoom.wallpaperId)

@Composable
private fun StudioHeader(title: String, coins: Int, onClose: () -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("$tag-back")) { Text("‹", fontSize = 24.sp, color = Ink) }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        Row(Modifier.clip(StudioPill).background(Butter).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkIcon(Mark.Coin, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("$coins", color = BubbleInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.heightIn(min = 40.dp).clip(StudioPill).background(if (selected) PeachDeep else CardWhite)
            .border(1.dp, if (selected) PeachDeep else Line, StudioPill)
            .clickable(onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) Color.White else Ink, fontSize = 13.sp,
    )
}

/** 상점: every item. Owned → '보유 중' + use/place/wear. Unowned items/outfits can be tried in a separate preview room. */
@Composable
fun ShopStudioScreen(state: GameState, service: GameService, scope: CoroutineScope, onClose: () -> Unit, onEarn: () -> Unit) {
    val editor = remember { LayoutEditor(realLayout(state)) }
    var version by remember { mutableIntStateOf(0) }
    fun edit(block: LayoutEditor.() -> Unit) { editor.block(); version++ }
    val preview = remember(version) { editor.current }
    var tab by remember { mutableStateOf("all") }
    var hideOwned by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var roomAsk by remember { mutableStateOf<String?>(null) }
    var applyAsk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val owned = state.owned
    val cart = Shop.cart(preview, owned)
    val total = Shop.total(cart)
    val short = total > state.coins
    Column(Modifier.fillMaxSize().background(Cream).testTag("shop-screen")) {
        StudioHeader(t("shop_title"), state.coins, onClose, "shop")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                RoomStage(state, preview, Modifier.fillMaxHeight().testTag("shop-preview"))
                Text(t("shop_preview"), Modifier.align(Alignment.TopStart).padding(8.dp).clip(StudioPill).background(BubbleInk.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp)
            }
            Text(t("shop_preview_note"), color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            // Rooms: unowned rooms can't be applied, even in the preview.
            Text(t("shop_rooms"), fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.presets.forEach { p ->
                    val has = Shop.roomOwned(p.wallpaperId, owned)
                    val label = L10n.preset(p.wallpaperId) + if (has) "" else " · 🔒 ${Shop.roomPrice(p.wallpaperId)}"
                    Chip(label, preview.roomId == p.wallpaperId, "shop-room-${p.wallpaperId}") {
                        if (has) edit { room(p.wallpaperId) } else roomAsk = p.wallpaperId
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all", "furniture", "outfit", "food").forEach { k -> Chip(t("shop_tab_$k"), tab == k, "shop-tab-$k") { tab = k } }
                Chip((if (hideOwned) "✓ " else "") + t("shop_hide_owned"), hideOwned, "shop-hide-owned") { hideOwned = !hideOwned }
            }
            Spacer(Modifier.height(12.dp))
            if (tab == "all" || tab == "food") {
                MedicineRow(state, scope, service)
                Spacer(Modifier.height(8.dp))
            }
            val items = Catalog.shop.filter {
                when (tab) {
                    "furniture" -> it.kind != ItemKind.OUTFIT && it.kind != ItemKind.FOOD
                    "outfit" -> it.kind == ItemKind.OUTFIT
                    "food" -> it.kind == ItemKind.FOOD
                    else -> true
                }
            }.filterNot { hideOwned && it.id in owned && it.kind != ItemKind.FOOD }
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        ShopCard(
                            item = item, owned = item.id in owned, count = state.foods.firstOrNull { it.defId == item.id }?.quantity ?: 0,
                            inPreview = preview.placements.any { it.defId == item.id } || preview.outfitId == item.id,
                            modifier = Modifier.weight(1f),
                            onTry = {
                                edit {
                                    if (item.kind == ItemKind.OUTFIT) wear(if (current.outfitId == item.id) null else item.id)
                                    else if (current.placements.any { it.defId == item.id }) remove(item.id)
                                    else place(item.id, 30 + (current.placements.size * 15) % 45, 72)
                                }
                            },
                            onUse = {
                                scope.launch {
                                    when (item.kind) {
                                        ItemKind.OUTFIT -> service.equipOutfit(item.id)
                                        ItemKind.FOOD -> service.feed(item.id)
                                        else -> service.placeDecor(item.id)
                                    }
                                }
                            },
                            onBuyOne = { scope.launch { if (!busy) { busy = true; service.buy(item.id); busy = false } } },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(96.dp))
        }
        // Cart bar
        if (cart.isNotEmpty() || editor.dirty) {
            Column(Modifier.fillMaxWidth().shadow(12.dp).background(CardWhite).padding(16.dp).testTag("shop-cart")) {
                if (short) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("shop_short"), color = PeachDeep, fontSize = 13.sp, modifier = Modifier.weight(1f).testTag("shop-short"))
                        TextButton(onClick = onEarn, modifier = Modifier.testTag("shop-earn")) { Text(t("shop_earn")) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { edit { revert() } }, modifier = Modifier.heightIn(min = 48.dp).testTag("shop-revert")) { Text(t("shop_revert"), color = Ink) }
                    TextButton(onClick = { edit { undo() } }, modifier = Modifier.heightIn(min = 48.dp).testTag("shop-undo")) { Text(t("shop_undo"), color = InkSoft) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { confirm = true },
                        enabled = cart.isNotEmpty() && !short && !busy,
                        shape = StudioPill,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("shop-buy-cart"),
                        colors = ButtonDefaults.buttonColors(containerColor = PeachDeep, contentColor = Color.White),
                    ) { Text(tf("shop_buy_total", cart.size, total)) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tf("shop_cart_confirm", cart.size, total)) },
            text = { Text(cart.joinToString(" · ") { L10n.item(it) }) },
            confirmButton = {
                TextButton(enabled = !busy, modifier = Modifier.testTag("shop-confirm-yes"), onClick = {
                    busy = true
                    scope.launch {
                        val r = service.buyAll(cart)
                        busy = false; confirm = false
                        if (r.ok) applyAsk = true
                    }
                }) { Text(t("shop_buy")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("shop_cancel")) } },
        )
    }
    if (applyAsk) {
        AlertDialog(
            onDismissRequest = { applyAsk = false },
            title = { Text(t("shop_bought_apply")) },
            confirmButton = { TextButton(modifier = Modifier.testTag("shop-apply"), onClick = { applyAsk = false; scope.launch { service.applyLayout(editor.current) } }) { Text(t("shop_apply")) } },
            dismissButton = { TextButton(onClick = { applyAsk = false }) { Text(t("shop_later")) } },
        )
    }
    roomAsk?.let { id -> RoomBuyDialog(id, state.coins, busy, onEarn, onDismiss = { roomAsk = null }) {
        busy = true
        scope.launch { val r = service.buyRoom(id); busy = false; roomAsk = null; if (r.ok) edit { room(id) } }
    } }
}

/** '방은 구매한 후에 적용할 수 있어요' + buy (coin check, confirm). */
@Composable
internal fun RoomBuyDialog(roomId: String, coins: Int, busy: Boolean, onEarn: () -> Unit, onDismiss: () -> Unit, onBuy: () -> Unit) {
    val price = Shop.roomPrice(roomId)
    val short = price > coins
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("room-buy-confirm"),
        title = { Text(t("shop_room_after_buy")) },
        text = {
            Column {
                Text(tf("shop_room_confirm", L10n.preset(roomId), price))
                if (short) Text(t("shop_short") + " · " + t("shop_earn_hint"), color = PeachDeep, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).testTag("room-short"))
            }
        },
        confirmButton = {
            if (short) TextButton(onClick = { onDismiss(); onEarn() }) { Text(t("shop_earn")) }
            else TextButton(enabled = !busy, onClick = onBuy, modifier = Modifier.testTag("room-buy-yes")) { Text(t("shop_buy")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("room-buy-cancel")) { Text(t("shop_cancel")) } },
    )
}

@Composable
private fun MedicineRow(state: GameState, scope: CoroutineScope, service: GameService) {
    val price = ConditionRules.MEDICINE_PRICE
    Row(
        Modifier.fillMaxWidth().clip(StudioCard).background(CardWhite).border(1.dp, Line, StudioCard).padding(12.dp).testTag("shop-medicine"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💊", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(t("shop_medicine"), color = Ink, fontWeight = FontWeight.Bold)
            Text(if (state.sick) "$price" else t("shop_medicine_hint"), color = InkSoft, fontSize = 12.sp)
        }
        Button(
            onClick = { scope.launch { service.takeMedicine() } },
            enabled = state.sick && state.coins >= price,
            shape = StudioPill,
            modifier = Modifier.heightIn(min = 48.dp).testTag("shop-medicine-use"),
            colors = ButtonDefaults.buttonColors(containerColor = MintSoft, contentColor = Ink),
        ) { Text(t("shop_use")) }
    }
}

@Composable
private fun ShopCard(item: ItemDef, owned: Boolean, count: Int, inPreview: Boolean, modifier: Modifier, onTry: () -> Unit, onUse: () -> Unit, onBuyOne: () -> Unit) {
    val food = item.kind == ItemKind.FOOD
    Column(
        modifier.shadow(3.dp, StudioCard, clip = false).clip(StudioCard).background(CardWhite)
            .then(if (inPreview) Modifier.border(2.dp, PeachDeep, StudioCard) else Modifier)
            .testTag("shop-item-${item.id}").padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            ArtImage(item.id, Modifier.fillMaxWidth().aspectRatio(1.1f), L10n.item(item.id))
            if (owned && !food) {
                Text(t("shop_owned"), Modifier.align(Alignment.TopStart).clip(StudioPill).background(Mint).padding(horizontal = 6.dp, vertical = 2.dp).testTag("owned-badge-${item.id}"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (food && count > 0) {
                Text(tf("shop_count", count), Modifier.align(Alignment.TopStart).clip(StudioPill).background(Mint).padding(horizontal = 6.dp, vertical = 2.dp).testTag("count-${item.id}"), color = Color.White, fontSize = 10.sp)
            }
        }
        Text(L10n.item(item.id), fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!owned || food) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkIcon(Mark.Coin, Modifier.size(12.dp)); Spacer(Modifier.width(2.dp))
                Text("${item.price}", fontSize = 12.sp, color = InkSoft)
            }
        }
        Spacer(Modifier.height(4.dp))
        val (label, tag, action) = when {
            food && count > 0 -> Triple(t("shop_use"), "use-${item.id}", onUse)
            food -> Triple(t("shop_buy"), "buy-${item.id}", onBuyOne)
            owned -> Triple(if (item.kind == ItemKind.OUTFIT) t("shop_wear") else t("shop_place"), "use-${item.id}", onUse)
            else -> Triple(if (inPreview) t("shop_in_preview") else if (item.kind == ItemKind.OUTFIT) t("shop_try_wear") else t("shop_try_place"), "try-${item.id}", onTry)
        }
        Text(
            label,
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(StudioPill).background(if (owned || food) MintSoft else PeachSoft)
                .clickable(onClick = action).testTag(tag).padding(vertical = 10.dp),
            textAlign = TextAlign.Center, fontSize = 12.sp, color = Ink, maxLines = 1,
        )
    }
}

/** 방꾸미기: only owned things; drag, snap, rotate, flip, remove, undo, '원래대로', save. No prices. */
@Composable
fun DecorateScreen(state: GameState, service: GameService, scope: CoroutineScope, onClose: () -> Unit, onShop: () -> Unit) {
    val editor = remember { LayoutEditor(realLayout(state)) }
    var version by remember { mutableIntStateOf(0) }
    fun edit(block: LayoutEditor.() -> Unit) { editor.block(); version++ }
    val layout = remember(version) { editor.current }
    var selected by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("furniture") }
    var noChangeHint by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val items = Shop.decoratable(state.owned)
    Column(Modifier.fillMaxSize().background(Cream).testTag("decorate-screen")) {
        StudioHeader(t("deco_title"), state.coins, onClose, "deco")
        Box(Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            RoomStage(
                state, layout, Modifier.fillMaxHeight().testTag("deco-stage"), selected, editable = true,
                onSelect = { selected = it },
                onMove = { id, x, y -> edit { move(id, x, y) } },
            )
        }
        Text(t("deco_hint"), color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sel = selected?.takeIf { s -> layout.placements.any { it.defId == s } }
            Chip(t("deco_rotate"), false, "deco-rotate") { sel?.let { edit { rotate(it) } } }
            Chip(t("deco_flip"), false, "deco-flip") { sel?.let { edit { flip(it) } } }
            Chip(t("deco_remove"), false, "deco-remove") { sel?.let { edit { remove(it) }; selected = null } }
            Spacer(Modifier.weight(1f))
            Chip(t("shop_undo"), false, "deco-undo") { edit { undo() } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("furniture", "outfit").forEach { k -> Chip(t("shop_tab_$k"), tab == k, "deco-tab-$k") { tab = k } }
            state.presets.filter { Shop.roomOwned(it.wallpaperId, state.owned) }.forEach { p ->
                Chip(L10n.preset(p.wallpaperId), layout.roomId == p.wallpaperId, "deco-room-${p.wallpaperId}") { edit { room(p.wallpaperId) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        val shown = items.filter { if (tab == "outfit") it.kind == ItemKind.OUTFIT else it.kind != ItemKind.OUTFIT }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp).testTag("deco-empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage("empty_gifts", Modifier.size(120.dp))
                    Text(t("deco_empty"), color = InkSoft, textAlign = TextAlign.Center)
                    TextButton(onClick = onShop) { Text(t("shop_open")) }
                }
            }
            shown.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        val on = layout.placements.any { it.defId == item.id } || layout.outfitId == item.id
                        Column(
                            Modifier.weight(1f).clip(StudioCard).background(if (on) PeachSoft else CardWhite)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    edit {
                                        if (item.kind == ItemKind.OUTFIT) wear(if (current.outfitId == item.id) null else item.id)
                                        else if (!on) place(item.id, 50, 70)
                                    }
                                    if (item.kind != ItemKind.OUTFIT) selected = item.id
                                }
                                .testTag("deco-item-${item.id}").padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtImage(item.id, Modifier.fillMaxWidth().aspectRatio(1f), L10n.item(item.id))
                            Text(L10n.item(item.id), fontSize = 11.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (noChangeHint && !editor.dirty) {
            Text(t("deco_no_changes"), color = Color(0xFFD9604F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(CardWhite).padding(horizontal = 16.dp, vertical = 8.dp).testTag("deco-save-hint"))
        }
        Row(Modifier.fillMaxWidth().shadow(12.dp).background(CardWhite).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { edit { revert() }; selected = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("deco-revert")) { Text(t("shop_revert"), color = Ink) }
            Spacer(Modifier.weight(1f))
            Button(
                // Always tappable; with nothing changed it explains why instead of silently ignoring the tap.
                onClick = { if (!editor.dirty) noChangeHint = true else scope.launch { service.applyLayout(editor.current); onClose() } },
                shape = StudioPill,
                modifier = Modifier.heightIn(min = 48.dp).testTag("deco-save"),
                colors = ButtonDefaults.buttonColors(containerColor = if (editor.dirty) PeachDeep else PeachDeep.copy(alpha = 0.55f), contentColor = Color.White),
            ) { Text(t("deco_save")) }
        }
    }
}
