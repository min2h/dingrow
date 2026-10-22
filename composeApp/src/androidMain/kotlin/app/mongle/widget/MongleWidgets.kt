package app.mongle.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import app.dingrow.R
import app.mongle.AppGraph
import app.mongle.l10n.AppLocale
import java.util.Locale
import app.mongle.appBrandName
import app.mongle.MainActivity
import app.mongle.logic.PetGesture
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.TimeOfDay
import app.mongle.ui.ArtBitmaps
import app.mongle.ui.petAct
import app.mongle.ui.petAssetId
import app.mongle.ui.roomAssetFor

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallPetWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargePetWidget()
}

class SmallPetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { SmallBody() }
    }
}

class LargePetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { LargeBody() }
    }
}

private fun currentState(): GameState? = if (AppGraph.ready) AppGraph.service.state.value else null

private val ink = ColorProvider(Color(0xFF5C4636))
private val cream = ColorProvider(Color(0xFFFFF6EE))
private val peach = ColorProvider(Color(0xFFFFE3D6))
private val mint = ColorProvider(Color(0xFFDDF3EA))
private val butter = ColorProvider(Color(0xFFFFF3C9))

@Composable
internal fun SmallBody(state: GameState? = currentState()) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cream)
            .padding(12.dp),
    ) {
        if (state == null) {
            Text(appBrandName(), style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PetImage(state, 64, react = true)
            Spacer(GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<OpenAppAction>()),
            ) {
                Text(s(R.string.widget_mood), style = TextStyle(color = ink, fontSize = 11.sp), maxLines = 1)
                Text(state.moodLabel, style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1)
            }
        }
        if (state.speech.isNotBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(state.speech, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(state.nextSchedule?.timeLabel ?: s(R.string.widget_next), style = TextStyle(color = ink, fontSize = 11.sp), maxLines = 1)
        Text(state.nextSchedule?.title ?: s(R.string.widget_none), style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Button(
            s(R.string.widget_done),
            actionRunCallback<CompleteAction>(),
            modifier = GlanceModifier.fillMaxWidth().cornerRadius(16.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = peach, contentColor = ink),
        )
    }
}

@Composable
internal fun LargeBody(state: GameState? = currentState()) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(cream).padding(12.dp),
    ) {
        if (state == null) {
            Text(appBrandName(), style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sf(R.string.widget_room, state.petName),
                style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "${state.coins}",
                style = TextStyle(color = ink, fontSize = 12.sp),
                modifier = GlanceModifier.background(butter).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        val context = LocalContext.current
        val night = state.timeOfDay == TimeOfDay.NIGHT
        val room = ArtBitmaps.decode(context, roomAssetFor(state.activeRoom.wallpaperId, night, square = true), 400)
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(22.dp).background(cream),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(room),
                s(R.string.widget_room_cd),
                GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                PetImage(state, 64, awake = state.nextSchedule != null, react = true)
            }
            if (state.speech.isNotBlank() || state.nextSchedule != null) {
                Text(
                    state.speech.ifBlank { state.nextSchedule?.title.orEmpty() },
                    style = TextStyle(color = ink, fontSize = 12.sp),
                    maxLines = 2,
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        if (state.confirmText.isNotBlank()) {
            Text(state.confirmText, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 2)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(s(R.string.widget_confirm), actionRunCallback<ConfirmBuyAction>(), modifier = GlanceModifier.defaultWeight(), colors = ButtonDefaults.buttonColors(backgroundColor = peach, contentColor = ink))
                Spacer(GlanceModifier.width(4.dp))
                Button(s(R.string.widget_cancel), actionRunCallback<CancelBuyAction>(), modifier = GlanceModifier.defaultWeight(), colors = ButtonDefaults.buttonColors(backgroundColor = mint, contentColor = ink))
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(s(R.string.widget_feed), actionRunCallback<FeedAction>(), modifier = GlanceModifier.defaultWeight().cornerRadius(16.dp), colors = ButtonDefaults.buttonColors(backgroundColor = peach, contentColor = ink))
                Spacer(GlanceModifier.width(6.dp))
                Button(s(R.string.widget_sell), actionRunCallback<SellAction>(), modifier = GlanceModifier.defaultWeight().cornerRadius(16.dp), colors = ButtonDefaults.buttonColors(backgroundColor = mint, contentColor = ink))
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(s(R.string.widget_today), actionRunCallback<BeginBuyAction>(), modifier = GlanceModifier.defaultWeight().cornerRadius(16.dp), colors = ButtonDefaults.buttonColors(backgroundColor = butter, contentColor = ink))
                Spacer(GlanceModifier.width(6.dp))
                Button(s(R.string.widget_decorate), actionRunCallback<PresetAction>(), modifier = GlanceModifier.defaultWeight().cornerRadius(16.dp), colors = ButtonDefaults.buttonColors(backgroundColor = peach, contentColor = ink))
            }
        }
    }
}

@Composable
private fun PetImage(state: GameState, size: Int, awake: Boolean = false, react: Boolean = false) {
    val context = LocalContext.current
    val pose = if (awake && state.pose == Pose.SLEEP) Pose.IDLE else state.pose
    val energy = if (awake) 80 else state.energy
    val act = petAct(state.stage, pose, energy, if (awake) "" else state.statusMessage)
    val bitmap = ArtBitmaps.decode(context, petAssetId(state.speciesId, state.stage, act), size * 2)
    val modifier = if (react) {
        GlanceModifier.size(size.dp).clickable(actionRunCallback<ReactAction>())
    } else {
        GlanceModifier.size(size.dp)
    }
    Image(ImageProvider(bitmap), state.petName, modifier)
}

@Composable
private fun localizedContext(): Context {
    val base = LocalContext.current
    val locale = if (AppLocale.code() == "ko") Locale.KOREAN else Locale.ENGLISH
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

@Composable
private fun s(id: Int): String = localizedContext().getString(id)

@Composable
private fun sf(id: Int, vararg args: Any): String {
    var index = 0
    return Regex("%[sd]").replace(localizedContext().getString(id)) {
        args.getOrNull(index++)?.toString().orEmpty()
    }.replace("%%", "%")
}

class ReactAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.react(PetGesture.TAP)
    }
}

class FeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.feed()
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.completeNext()
    }
}

class SellAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.sellPendingOrLatest()
    }
}

class BeginBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.beginBuyToday()
    }
}

class ConfirmBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.confirmBuy()
    }
}

class CancelBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.cancelBuy()
    }
}

class PresetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.cyclePreset()
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

class AddScheduleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("route", "add-schedule")
        }
        context.startActivity(intent)
    }
}
