package app.mongle.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.model.GameState

/** Pastel gift box drawn in code (ribbon + bow). */
@Composable
internal fun GiftBox(modifier: Modifier, sparkle: Boolean = false) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val body = if (sparkle) Color(0xFFFFD36E) else Color(0xFFF7A8B8)
        val lid = if (sparkle) Color(0xFFFFC23D) else Color(0xFFF28DA3)
        val ribbon = Color(0xFFFFF4E0)
        drawRoundRect(body, Offset(w * 0.12f, h * 0.42f), Size(w * 0.76f, h * 0.52f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
        drawRoundRect(lid, Offset(w * 0.06f, h * 0.30f), Size(w * 0.88f, h * 0.16f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        drawRect(ribbon, Offset(w * 0.44f, h * 0.30f), Size(w * 0.12f, h * 0.64f))
        drawCircle(ribbon, w * 0.13f, Offset(w * 0.36f, h * 0.22f))
        drawCircle(ribbon, w * 0.13f, Offset(w * 0.64f, h * 0.22f))
        drawCircle(lid, w * 0.06f, Offset(w * 0.5f, h * 0.27f))
    }
}

/** 몽이 carrying a gift box; tap opens it. Bobs gently like a game pickup. */
@Composable
internal fun GiftCarrier(state: GameState, onOpen: () -> Unit) {
    val bob = rememberInfiniteTransition(label = "gift").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Row(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp), clip = false).clip(RoundedCornerShape(24.dp))
            .background(PeachSoft).clickable(onClick = onOpen).testTag("gift-carrier").padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp)) {
            PetFigure(state.speciesId, state.stage, PetAct.HAPPY, state.outfitId, Modifier.fillMaxSize(), animate = false, breath = 0.35f, walk = 0.2f)
            GiftBox(Modifier.size(40.dp).align(Alignment.BottomEnd).graphicsLayer { translationY = -bob.value * 8.dp.toPx(); rotationZ = (bob.value - 0.5f) * 10f }, state.pendingSparkle)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("gift_carry_title"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(t("gift_tap_open"), color = PeachDeep, fontSize = 13.sp)
        }
    }
}

/** '선물이 더 좋아져요!' — shown once per new level, with the new gift odds. */
@Composable
internal fun LevelUpDialog(state: GameState, onDone: () -> Unit) {
    val w = app.mongle.model.EconomyConfig.weightsFor(state.level)
    val total = w.sum().toFloat()
    fun pct(v: Int) = ((v / total * 1000).toInt() / 10f).toString() + "%"
    val coins = app.mongle.model.EconomyConfig.coinRange(state.level)
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(28.dp)).background(CardWhite).padding(24.dp).testTag("levelup"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(110.dp)) {
                PetFigure(state.speciesId, state.stage, PetAct.HAPPY, state.outfitId, Modifier.fillMaxSize(), animate = false, breath = 0.35f, walk = 0.2f)
                GiftBox(Modifier.size(44.dp).align(Alignment.BottomEnd), sparkle = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(tf("levelup_title", state.level), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(t("levelup_body"), color = PeachDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(tf("levelup_detail", pct(w[2]), pct(w[3]), coins.first, coins.last), color = InkSoft, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                t("levelup_ok"),
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(PeachDeep).clickable(onClick = onDone).testTag("levelup-ok").padding(vertical = 14.dp),
                color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Explains why before the OS POST_NOTIFICATIONS dialog. */
@Composable
internal fun NotifExplainDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("notif-explain"),
        title = { Text(t("notif_explain_title")) },
        text = { Text(t("notif_explain_body")) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onAllow, modifier = Modifier.testTag("notif-allow")) { Text(t("notif_explain_yes")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onLater, modifier = Modifier.testTag("notif-later")) { Text(t("notif_explain_later")) } },
    )
}
