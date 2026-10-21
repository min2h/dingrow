package app.mongle.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import app.mongle.AppGraph
import app.mongle.MainActivity
import app.mongle.R
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.ui.hexColor

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

private val ink = ColorProvider(Color(0xFF4A3728))
private val cream = ColorProvider(Color(0xFFFFF8F3))

@Composable
private fun SmallBody() {
    val state = currentState()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cream)
            .padding(10.dp)
            .clickable(actionRunCallback<OpenAppAction>()),
    ) {
        if (state == null) {
            Text("몽글", style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(poseDrawable(state.pose)), state.petName, GlanceModifier.size(52.dp))
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text(state.petName, style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1)
                Text(state.moodLabel, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(state.nextSchedule?.title ?: "일정 없음", style = TextStyle(color = ink, fontSize = 13.sp), maxLines = 1)
        Text(state.nextSchedule?.timeLabel ?: "눌러서 추가", style = TextStyle(color = ink, fontSize = 11.sp), maxLines = 1)
        if (state.pendingGift != null) {
            Text("선물 도착", style = TextStyle(color = ink, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button("먹이", actionRunCallback<FeedAction>(), modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(4.dp))
            Button("완료", actionRunCallback<CompleteAction>(), modifier = GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun LargeBody() {
    val state = currentState()
    Column(
        modifier = GlanceModifier.fillMaxSize().background(cream).padding(12.dp),
    ) {
        if (state == null) {
            Text("몽글", style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
            return@Column
        }
        val wall = ColorProvider(hexColor(state.wallHex))
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(150.dp).cornerRadius(18.dp).background(wall),
            contentAlignment = Alignment.Center,
        ) {
            state.activeRoom.bedId?.let { id ->
                furnitureDrawable(id)?.let { drawable ->
                    Image(
                        ImageProvider(drawable),
                        "침대",
                        GlanceModifier.size(64.dp),
                    )
                }
            }
            Image(ImageProvider(poseDrawable(state.pose)), state.petName, GlanceModifier.size(84.dp))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            "${state.petName} · ${state.moodLabel} · ${state.activeRoom.name}",
            style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp),
            maxLines = 1,
        )
        Text(
            state.outing?.label ?: (state.nextSchedule?.let { "${it.title} ${it.timeLabel}" } ?: "다음 일정이 없어요"),
            style = TextStyle(color = ink, fontSize = 12.sp),
            maxLines = 1,
        )
        val gift = state.pendingGift
        if (gift != null) {
            Text("선물 ${gift.name} ${gift.sellPrice}코인", style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1)
        }
        if (state.confirmText.isNotBlank()) {
            Text(state.confirmText, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 2)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button("확인", actionRunCallback<ConfirmBuyAction>(), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Button("취소", actionRunCallback<CancelBuyAction>(), modifier = GlanceModifier.defaultWeight())
            }
        } else {
            Text("오늘 ${state.todayOffer.item.name} ${state.todayOffer.price}코인", style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button("먹이", actionRunCallback<FeedAction>(), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Button("완료", actionRunCallback<CompleteAction>(), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Button("팔기", actionRunCallback<SellAction>(), modifier = GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button("구매", actionRunCallback<BeginBuyAction>(), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Button("방", actionRunCallback<PresetAction>(), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Button("일정+", actionRunCallback<AddScheduleAction>(), modifier = GlanceModifier.defaultWeight())
            }
        }
        if (state.statusMessage.isNotBlank()) {
            Text(state.statusMessage, style = TextStyle(color = ink, fontSize = 11.sp), maxLines = 2)
        }
    }
}

private fun poseDrawable(pose: Pose): Int = when (pose) {
    Pose.EGG -> R.drawable.pet_egg
    Pose.HAPPY -> R.drawable.pet_happy
    Pose.SAD -> R.drawable.pet_sad
    Pose.SLEEP -> R.drawable.pet_sleep
    Pose.WALK -> R.drawable.pet_walk
    Pose.OUT -> R.drawable.pet_out
    Pose.IDLE -> R.drawable.pet_idle
}

private fun furnitureDrawable(id: String): Int? = when (id) {
    "bed_cloud" -> R.drawable.furn_bed
    "bed_wood" -> R.drawable.furn_bed_wood
    "rug_star", "rug_leaf" -> R.drawable.furn_rug
    "lamp_moon", "lamp_sun" -> R.drawable.furn_lamp
    else -> null
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
