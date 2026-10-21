package app.mongle.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScheduledNotice(
    val id: String,
    val scheduleId: String,
    val title: String,
    val body: String,
    val triggerAtMillis: Long,
)

/**
 * 플랫폼 알림과 위젯 갱신. 실제 구현은 androidMain / iosMain에 있고,
 * 위젯 프로세스가 뜨기 전에 앱이 훅을 연결한다.
 */
expect class PlatformNotifier() {
    fun sync(notices: List<ScheduledNotice>)
    fun notifyNow(title: String, body: String)
    fun reloadWidgets()
}

/** 위젯에서 '일정 추가'를 누르면 앱이 이 값을 보고 작성 화면을 연다. */
object NavigationBus {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route

    fun request(route: String) {
        _route.value = route
    }

    fun consume() {
        _route.value = null
    }
}
