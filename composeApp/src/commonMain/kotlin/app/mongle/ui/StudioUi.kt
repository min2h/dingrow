package app.mongle.ui

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.layout.PaddingValues

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
    petScale: Float = 0.28f,
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).fillMaxWidth(petScale).aspectRatio(1f).testTag("room-pet"),
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
    var cartPx by remember { mutableIntStateOf(0) }
    val leave = rememberDiscardGuard(cart.isNotEmpty() || editor.dirty, onClose)
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Cream).testTag("shop-screen")) {
    val stageH = pinnedStageHeight(maxHeight)
    Column(Modifier.fillMaxSize()) {
        StudioHeader(t("shop_title"), state.coins, leave, "shop")
        // Pinned preview: stays put while only the list below scrolls.
        Column(Modifier.fillMaxWidth().background(Cream).padding(horizontal = 16.dp).testTag("shop-pinned")) {
            Box(Modifier.fillMaxWidth().height(stageH), contentAlignment = Alignment.Center) {
                // 몽이 is shown large in the shop; trying on an outfit zooms in further so the fit is easy to see.
                val tryingOn = preview.outfitId != null && preview.outfitId != state.outfitId
                val petScale by androidx.compose.animation.core.animateFloatAsState(if (tryingOn) 0.62f else 0.46f, label = "shop-pet-zoom")
                RoomStage(state, preview, Modifier.fillMaxHeight().testTag("shop-preview"), petScale = petScale)
                Text(t("shop_preview"), Modifier.align(Alignment.TopStart).padding(8.dp).clip(StudioPill).background(BubbleInk.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp)
            }
            Text(t("shop_preview_note"), color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp).testTag("shop-preview-note"))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("shop-list")) {
            Spacer(Modifier.height(12.dp))
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
            // Room for the overlaid cart bar so the last row's buttons stay reachable.
            Spacer(Modifier.height(with(density) { cartPx.toDp() } + 16.dp).testTag("shop-list-end"))
        }
    }
        // Cart bar (overlay at the bottom; list is padded by its measured height)
        if (cart.isNotEmpty() || editor.dirty) {
            // One compact row: small reset/undo icons + a single buy button that also carries the coin shortfall.
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { cartPx = it.height }
                    .shadow(12.dp).background(CardWhite).padding(horizontal = 8.dp, vertical = 6.dp).testTag("shop-cart"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { edit { revert() } }, modifier = Modifier.size(48.dp).semantics { contentDescription = L10n.t("shop_revert") }.testTag("shop-revert"), contentPadding = PaddingValues(0.dp)) { ArrowIcon(undo = false) }
                TextButton(onClick = { edit { undo() } }, modifier = Modifier.size(48.dp).semantics { contentDescription = L10n.t("shop_undo") }.testTag("shop-undo"), contentPadding = PaddingValues(0.dp)) { ArrowIcon(undo = true) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (short) onEarn() else confirm = true },
                    enabled = cart.isNotEmpty() && !busy,
                    shape = StudioPill,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(if (short) "shop-earn" else "shop-buy-cart"),
                    colors = ButtonDefaults.buttonColors(containerColor = if (short) Butter else PeachDeep, contentColor = if (short) BubbleInk else Color.White),
                ) {
                    if (short) Text(tf("shop_need_more", total - state.coins), modifier = Modifier.testTag("shop-short"), maxLines = 1)
                    else Text(tf("shop_buy_total", cart.size, total), maxLines = 1)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val stageH = pinnedStageHeight(maxHeight)
    Column(Modifier.fillMaxSize().background(Cream).testTag("decorate-screen")) {
        val leave = rememberDiscardGuard(editor.dirty, onClose)
        StudioHeader(t("deco_title"), state.coins, leave, "deco")
        Box(Modifier.fillMaxWidth().height(stageH).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
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
}

/** Pinned room height: ~25% of the screen (2+ full item rows stay visible on a 1080x2400 phone), clamped so small phones keep room for the list. */
internal fun pinnedStageHeight(screen: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    (screen * 0.25f).coerceIn(140.dp, 240.dp)

/** Reset (circular arrow) and Undo (hook arrow) drawn in the same 22dp box and stroke so they read as one size. */
@Composable
internal fun ArrowIcon(undo: Boolean, color: Color = Ink) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp).testTag(if (undo) "icon-undo" else "icon-reset")) {
        val w = size.width; val st = w * 0.11f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = st, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path()
        if (!undo) {
            val r = w * 0.38f; val c = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
            drawArc(color, startAngle = -60f, sweepAngle = -280f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = stroke)
            // arrowhead at the arc start (top-right)
            val tip = androidx.compose.ui.geometry.Offset(c.x + r * 0.5f, c.y - r * 0.866f)
            path.moveTo(tip.x - w * 0.20f, tip.y - w * 0.04f); path.lineTo(tip.x, tip.y); path.lineTo(tip.x - w * 0.02f, tip.y + w * 0.20f)
        } else {
            // hook: from bottom-right up and around to the left, arrowhead pointing left
            path.moveTo(w * 0.86f, w * 0.86f)
            path.cubicTo(w * 0.86f, w * 0.40f, w * 0.60f, w * 0.30f, w * 0.14f, w * 0.30f)
            path.moveTo(w * 0.32f, w * 0.12f); path.lineTo(w * 0.14f, w * 0.30f); path.lineTo(w * 0.32f, w * 0.48f)
        }
        drawPath(path, color, style = stroke)
    }
}
