package app.mongle

import java.util.Locale

actual fun appBrandName(): String =
    if (Locale.getDefault().language == "ko") "딩그로" else "Dingrow"
