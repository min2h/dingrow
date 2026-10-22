package app.mongle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.auth.AuthResult
import app.mongle.auth.AuthService
import app.mongle.auth.AuthSession
import app.mongle.model.Pose
import app.mongle.model.Stage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

val LocalAuth = staticCompositionLocalOf<AuthService?> { null }

/**
 * Shows login until there is a session, a short welcome-back splash on auto-login, a guest
 * confirmation after first guest sign-in, then [content].
 */
@Composable
fun AuthGate(auth: AuthService, splashMillis: Long = 1_200, content: @Composable () -> Unit) {
    val session by auth.session.collectAsState()
    val restored by auth.restored.collectAsState()
    var splashDone by remember { mutableStateOf(!auth.autoLoggedIn) }
    var guestIntro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!splashDone) { delay(splashMillis); splashDone = true } }
    CompositionLocalProvider(LocalAuth provides auth) {
        when {
            !restored -> Box(Modifier.fillMaxSize().background(Cream))
            session == null -> LoginScreen(auth, onGuest = { guestIntro = true })
            !splashDone -> SplashScreen(session!!)
            guestIntro && session!!.provider == "guest" -> GuestScreen(session!!, onContinue = { guestIntro = false })
            else -> content()
        }
    }
}

private val LoginBg = Brush.verticalGradient(listOf(Color(0xFFFFF0E8), Color(0xFFFFFAF4)))

@Composable
fun LoginScreen(auth: AuthService, onGuest: () -> Unit) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun handle(r: AuthResult) {
        busy = false
        when (r) {
            is AuthResult.Unavailable -> notice = r.messageKey
            is AuthResult.Failed -> notice = "login_failed"
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize().background(LoginBg).safeDrawingPadding().testTag("login")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(250.dp).shadow(14.dp, RoundedCornerShape(30.dp), spotColor = Color(0x33000000)).clip(RoundedCornerShape(30.dp))) {
                Image(painterResource(Art.room("wood", night = false, square = true)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                StillPet("base", Stage.BABY, Pose.IDLE, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(120.dp))
                SpeechBubble(t("login_bubble"), Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 52.dp), tailX = 150.dp, maxWidth = 210.dp, fontSize = 13)
            }
            Spacer(Modifier.height(28.dp))
            Text(t("login_title"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(t("login_sub"), fontSize = 12.sp, color = InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            LoginButton(t("login_google"), Color.White, Ink, border = Color(0xFFE6DCD4), tag = "login-google", icon = { GoogleMark() }) {
                busy = true; scope.launch { handle(auth.signInGoogle()) }
            }
            if (auth.appleAvailable) {
                Spacer(Modifier.height(12.dp))
                LoginButton(t("login_apple"), Color(0xFF181818), Color.White, tag = "login-apple", icon = { AppleMark() }) {
                    busy = true; scope.launch { handle(auth.signInApple()) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().then(if (notice != null) Modifier.border(2.5.dp, PeachDeep, RoundedCornerShape(30.dp)).padding(4.dp) else Modifier)) {
                LoginButton(t("login_guest"), PeachSoft, Color(0xFFC45C4C), tag = "login-guest", shadow = false) {
                    scope.launch { auth.signInGuest(); onGuest() }
                }
            }
            if (auth.debugFake) {
                Text(t("login_debug_fake"), fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp).clickable { scope.launch { handle(auth.signInFake()) } }.testTag("login-fake"))
            }
            Spacer(Modifier.height(24.dp))
            Text(t("login_terms"), fontSize = 10.sp, color = InkSoft, textAlign = TextAlign.Center)
            Text(t("login_apple_note"), fontSize = 10.sp, color = InkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        }
        AnimatedVisibility(notice != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 30.dp, end = 30.dp)) {
            Column(
                Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)).background(Color(0xFF5C4636), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 14.dp).testTag("login-notice"),
            ) {
                Text(t(notice ?: "login_google_unavailable"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(t("login_fallback_sub"), color = Color(0xFFF0E1D6), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (busy) LaunchedEffect(Unit) { delay(15_000); busy = false }
    }
}

@Composable
private fun LoginButton(label: String, bg: Color, fg: Color, border: Color? = null, tag: String, shadow: Boolean = true, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .then(if (shadow) Modifier.shadow(6.dp, shape, spotColor = Color(0x22000000)) else Modifier)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clip(shape).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Box(Modifier.align(Alignment.CenterStart).padding(start = 52.dp)) { icon() }
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun GoogleMark() {
    Canvas(Modifier.size(18.dp)) {
        val cols = listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335))
        val sweeps = listOf(-40f to 85f, 45f to 90f, 135f to 65f, 200f to 120f)
        val st = size.width * 0.19f
        sweeps.forEachIndexed { i, (a, sw) -> drawArc(cols[i], a, sw, false, topLeft = Offset(st / 2, st / 2), size = Size(size.width - st, size.height - st), style = Stroke(st)) }
        drawRect(cols[0], Offset(size.width / 2, size.height / 2 - st / 2), Size(size.width / 2 - st / 4, st))
    }
}

@Composable
private fun AppleMark() {
    Canvas(Modifier.size(18.dp)) {
        val r = size.width / 2
        drawCircle(Color.White, r * 0.62f, Offset(r * 0.7f, r * 1.15f)); drawCircle(Color.White, r * 0.62f, Offset(r * 1.3f, r * 1.15f))
        drawCircle(Color.White, r * 0.3f, Offset(r * 1.15f, r * 0.25f))
    }
}

@Composable
fun SplashScreen(session: AuthSession) {
    val pulse = rememberInfiniteTransition(label = "dots")
    val phase by pulse.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFECE2), Color(0xFFFFF8F0)))).testTag("splash")) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(150.dp))
            Box(Modifier.size(280.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
                StillPet("base", Stage.BABY, Pose.HAPPY, Modifier.size(170.dp))
            }
            Text(t("app_name"), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(t("splash_tagline"), fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i -> val a = if (phase.toInt() == i) 1f else 0.4f; Box(Modifier.size(8.dp).background(Peach.copy(alpha = a), CircleShape)) }
            }
            Spacer(Modifier.height(22.dp))
            val name = session.displayName
            Text(if (name.isNotBlank()) tf("splash_welcome_name", name) else t("splash_welcome"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(tf("splash_auto", t("provider_" + session.provider)), fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 6.dp, bottom = 80.dp))
        }
    }
}

@Composable
fun GuestScreen(session: AuthSession, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().padding(horizontal = 22.dp).testTag("guest"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("guest_header"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        StillPet("base", Stage.BABY, Pose.HAPPY, Modifier.size(120.dp))
        Text(t("guest_title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 12.dp))
        Text(t("guest_sub"), fontSize = 12.sp, color = InkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        val created = Instant.fromEpochMilliseconds(session.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val rows = listOf(
            t("guest_row_id") to shortId(session.userId),
            t("guest_row_device") to session.device,
            t("guest_row_storage") to t("guest_storage_value"),
            t("guest_row_created") to tf("date_ymd", created.year, created.monthNumber, created.dayOfMonth),
        )
        Column(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color(0x22000000)).background(CardWhite, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 6.dp)) {
            rows.forEachIndexed { i, (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, fontSize = 13.sp, color = InkSoft)
                    Spacer(Modifier.width(12.dp))
                    Text(v, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 1)
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0E8E2)))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(t("guest_note"), fontSize = 12.sp, color = Color(0xFFA07846), modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF4E2), RoundedCornerShape(18.dp)).padding(18.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(PeachDeep).clickable(onClick = onContinue).testTag("guest-continue"), contentAlignment = Alignment.Center) {
            Text(t("guest_continue"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun shortId(id: String): String = if (id.length <= 16) id else id.take(10) + "…" + id.takeLast(4)
