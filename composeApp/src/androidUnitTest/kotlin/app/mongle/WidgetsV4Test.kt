package app.mongle

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import app.mongle.logic.ScheduleDraft
import app.mongle.model.RepeatRule
import app.mongle.data.MemoryGameStore
import app.mongle.service.GameService
import app.mongle.widget.LargeBody
import app.mongle.widget.MediumBody
import app.mongle.widget.SmallBody
import app.mongle.widget.WidgetScene
import app.mongle.widget.scheduleUri
import app.mongle.widget.widgetRows
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class WidgetsV4Test {
    @After fun reset() { app.mongle.l10n.AppLocale.preference = "system" }

    private fun service(): Pair<GameService, MutableClock> = runBlocking {
        val clock = MutableClock(System.currentTimeMillis())
        val s = GameService(MemoryGameStore(), clock, TimeZone.currentSystemDefault())
        s.completeOnboarding("몽이")
        s.addDraft(ScheduleDraft("병원", clock.now + 25 * 60_000, RepeatRule.NONE, category = "health", place = "서울내과"))
        s.addDraft(ScheduleDraft("장보기", clock.now + 90 * 60_000, RepeatRule.NONE, category = "chores", place = "마트"))
        s.addDraft(ScheduleDraft("약 먹기", clock.now - 30 * 60_000, RepeatRule.NONE, category = "health"))
        val doneId = s.state.value.schedules.first { it.title == "약 먹기" }.id
        s.completeSchedule(doneId)
        AppGraph.installForTests(s)
        s to clock
    }

    @Test fun emphasisSpansAreBoldAccent() {
        val cs = WidgetScene.emphasisSpans("**3시** **서울내과** 가야 해요") as Spanned
        val bold = cs.getSpans(0, cs.length, StyleSpan::class.java)
        val col = cs.getSpans(0, cs.length, ForegroundColorSpan::class.java)
        assertEquals(2, bold.size); assertEquals(2, col.size)
        assertEquals("3시 서울내과 가야 해요", cs.toString())
    }

    @Test fun bubbleAnchoredOverHeadAndInside() {
        val (s, _) = service()
        val ctx = RuntimeEnvironment.getApplication()
        val st = s.state.value
        assertTrue(st.speech.isNotBlank())
        val r = WidgetScene.render(ctx, st, 600, 400, 2f, levelPill = true)
        val b = r.bubbleRect!!
        assertTrue(b.left >= 0 && b.right <= 600 && b.top >= 0)
        assertTrue("bubble above head", b.bottom <= r.petRect.top + r.petRect.height() * 0.2f)
        assertTrue(b.centerX() in r.petRect.left - 200..r.petRect.right + 200)
        val off = WidgetScene.render(ctx, st, 600, 400, 2f, showBubble = false)
        assertNull(off.bubbleRect)
    }

    @Test fun rowsAndDeepLink() {
        val (s, _) = service()
        val rows = widgetRows(s.state.value)
        assertEquals(3, rows.size)
        assertTrue(rows.any { it.done })
        val id = rows.first().view.id
        val uri = scheduleUri(id)
        assertEquals(id, scheduleIdFromUri(uri))
        assertNull(scheduleIdFromUri(android.net.Uri.parse("dingrow://schedule/add")))
        assertNull(scheduleIdFromUri(android.net.Uri.parse("https://x/schedule/1")))
    }


    @Test fun screenshotsAllSizesKoEn() = runBlocking {
        val (s, _) = service()
        val ctx = RuntimeEnvironment.getApplication()
        for (lang in listOf("ko", "en")) {
            app.mongle.l10n.AppLocale.preference = lang
            s.selfTalk()
            val p = if (lang == "ko") "" else "en-"
            saveGlance(ctx, "${p}widget-v4-small.png", 170, 170) { SmallBody(s.state.value) }
            saveGlance(ctx, "${p}widget-v4-medium.png", 340, 160) { MediumBody(s.state.value) }
            saveGlance(ctx, "${p}widget-v4-large.png", 340, 360) { LargeBody(s.state.value) }
        }
        app.mongle.l10n.AppLocale.preference = "ko"
        s.updateSettings(s.state.value.settings.copy(widgetList = false, widgetBubble = false, widgetCondition = false))
        saveGlance(ctx, "widget-v4-large-minimal.png", 340, 360) { LargeBody(s.state.value) }
    }
}

