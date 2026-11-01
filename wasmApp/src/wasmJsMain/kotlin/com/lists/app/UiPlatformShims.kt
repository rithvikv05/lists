package androidx.compose.ui.platform

import androidx.compose.runtime.compositionLocalOf
import com.lists.app.LocalWebContext
import com.lists.app.WebContext

class Configuration(
    val screenWidthDp: Int = try { kotlinx.browser.window.innerWidth } catch (_: Throwable) { 1200 },
    val screenHeightDp: Int = try { kotlinx.browser.window.innerHeight } catch (_: Throwable) { 800 }
)

val LocalContext = LocalWebContext
val LocalConfiguration = compositionLocalOf { Configuration() }

