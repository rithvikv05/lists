package com.lists.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.get
import org.w3c.dom.set

fun createVector(
    name: String,
    viewportWidth: Float,
    viewportHeight: Float,
    pathData: String,
    defaultWidth: Float = 24f,
    defaultHeight: Float = 24f
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth.dp,
        defaultHeight = defaultHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

object R {
    object drawable {
        val ic_type_movie = createVector(
            "ic_type_movie", 960f, 960f,
            "M240,760v40q0,17 -11.5,28.5T200,840q-17,0 -28.5,-11.5T160,800v-640q0,-17 11.5,-28.5T200,120q17,0 28.5,11.5T240,160v40h80v-40q0,-17 11.5,-28.5T360,120h240q17,0 28.5,11.5T640,160v40h80v-40q0,-17 11.5,-28.5T760,120q17,0 28.5,11.5T800,160v640q0,17 -11.5,28.5T760,840q-17,0 -28.5,-11.5T720,800v-40h-80v40q0,17 -11.5,28.5T600,840L360,840q-17,0 -28.5,-11.5T320,800v-40h-80ZM240,680h80v-80h-80v80ZM240,520h80v-80h-80v80ZM240,360h80v-80h-80v80ZM640,680h80v-80h-80v80ZM640,520h80v-80h-80v80ZM640,360h80v-80h-80v80ZM400,760h160v-560L400,200v560ZM400,200h160,-160Z"
        )
        val ic_type_tv_show = createVector(
            "ic_type_tv_show", 960f, 960f,
            "M160,760q-33,0 -56.5,-23.5T80,680v-480q0,-33 23.5,-56.5T160,120h640q33,0 56.5,23.5T880,200v480q0,33 -23.5,56.5T800,760L640,760v40q0,17 -11.5,28.5T600,840L360,840q-17,0 -28.5,-11.5T320,800v-40L160,760ZM160,680h640v-480L160,200v480ZM160,680v-480,480Z"
        )
        val ic_type_book = createVector(
            "ic_type_book", 960f, 960f,
            "M240,880q-33,0 -56.5,-23.5T160,800v-640q0,-33 23.5,-56.5T240,80h480q33,0 56.5,23.5T800,160v640q0,33 -23.5,56.5T720,880L240,880ZM240,800h480v-640h-80v245q0,12 -10,17.5t-20,-0.5l-49,-30q-10,-6 -20.5,-6t-20.5,6l-49,30q-10,6 -20.5,0.5T440,405v-245L240,160v640ZM240,800v-640,640Z"
        )
        val ic_type_videogame = createVector(
            "ic_type_videogame", 960f, 960f,
            "M160,720q-33,0 -56.5,-23.5T80,640v-320q0,-33 23.5,-56.5T160,240h640q33,0 56.5,23.5T880,320v320q0,33 -23.5,56.5T800,720L160,720ZM160,640h640v-320L160,320v320ZM280,520v40q0,17 11.5,28.5T320,600q17,0 28.5,-11.5T360,560v-40h40q17,0 28.5,-11.5T440,480q0,-17 -11.5,-28.5T400,440h-40v-40q0,-17 -11.5,-28.5T320,360q-17,0 -28.5,11.5T280,400v40h-40q-17,0 -28.5,11.5T200,480q0,17 11.5,28.5T240,520h40ZM580,600q25,0 42.5,-17.5T640,540q0,-25 -17.5,-42.5T580,480q-25,0 -42.5,17.5T520,540q0,25 17.5,42.5T580,600ZM700,480q25,0 42.5,-17.5T760,420q0,-25 -17.5,-42.5T700,360q-25,0 -42.5,17.5T640,420q0,25 17.5,42.5T700,480ZM160,640v-320,320Z"
        )
        val ic_type_anime = createVector(
            "ic_type_anime", 24f, 24f,
            "M3.716,18.025L20.284,18.025C21.845,18.025 22.691,17.17 22.691,15.618L22.691,6.005C22.691,4.445 21.845,3.598 20.284,3.598L3.716,3.598C2.155,3.598 1.309,4.445 1.309,6.005L1.309,15.618C1.309,17.17 2.155,18.025 3.716,18.025ZM3.865,16.24C3.351,16.24 3.093,15.991 3.093,15.468L3.093,6.146C3.093,5.624 3.351,5.383 3.865,5.383L20.143,5.383C20.658,5.383 20.915,5.624 20.915,6.146L20.915,15.468C20.915,15.991 20.658,16.24 20.143,16.24L3.865,16.24ZM12.398,14.074C12.564,14.074 12.681,13.949 12.722,13.792C13.137,11.376 13.386,10.944 15.877,10.621C16.051,10.596 16.175,10.463 16.175,10.289C16.175,10.123 16.051,9.998 15.877,9.965C13.419,9.641 13.096,9.201 12.722,6.811C12.689,6.636 12.564,6.512 12.398,6.512C12.232,6.512 12.1,6.628 12.066,6.802C11.651,9.218 11.386,9.633 8.912,9.965C8.738,9.998 8.613,10.123 8.613,10.289C8.613,10.463 8.738,10.587 8.912,10.621C11.369,10.919 11.693,11.359 12.066,13.783C12.1,13.949 12.224,14.074 12.398,14.074ZM9.078,15.41C9.194,15.41 9.294,15.335 9.319,15.211C9.526,14.016 9.468,13.982 10.763,13.767C10.879,13.75 10.962,13.65 10.962,13.526C10.962,13.41 10.879,13.31 10.763,13.293C9.468,13.086 9.518,13.044 9.319,11.866C9.294,11.733 9.203,11.642 9.078,11.642C8.954,11.642 8.862,11.733 8.837,11.866C8.622,13.028 8.688,13.069 7.393,13.293C7.26,13.318 7.186,13.41 7.186,13.526C7.186,13.667 7.26,13.75 7.418,13.767C8.688,13.966 8.622,14.016 8.837,15.194C8.862,15.327 8.945,15.41 9.078,15.41ZM7.7,20.988L16.3,20.988C16.756,20.988 17.113,20.615 17.113,20.183C17.113,19.743 16.756,19.361 16.3,19.361L7.7,19.361C7.252,19.361 6.887,19.743 6.887,20.183C6.887,20.615 7.252,20.988 7.7,20.988Z"
        )
        val ic_type_manga = createVector(
            "ic_type_manga", 960f, 960f,
            "M270,880q-45,0 -77.5,-30.5T160,774v-558q0,-38 23.5,-68t61.5,-38l300,-59q37,-8 66,16t29,62v477q0,29 -18,51.5T576,685l-315,63q-9,2 -15,9.5t-6,16.5q0,11 9,18.5t21,7.5h450v-600q0,-17 11.5,-28.5T760,160q17,0 28.5,11.5T800,200v600q0,33 -23.5,56.5T720,880L270,880ZM360,647 L560,608v-478l-200,39v478ZM280,663v-478l-15,3q-11,2 -18,9.5t-7,18.5v457q5,-2 10.5,-3.5T261,667l19,-4ZM240,188v485,-485Z"
        )
        val ic_pin = createVector(
            "ic_pin", 960f, 960f,
            "m640,480 l80,80v80L520,640v240l-40,40 -40,-40v-240L240,640v-80l80,-80v-280h-40v-80h400v80h-40v280ZM354,560h252l-46,-46v-314L400,200v314l-46,46ZM480,560Z"
        )
        val ic_unpin = createVector(
            "ic_unpin", 960f, 960f,
            "M680,120v80h-40v327l-80,-80v-247L400,200v87l-87,-87 -33,-33v-47h400ZM480,920l-40,-40v-240L240,640v-80l80,-80v-46L56,168l56,-56 736,736 -58,56 -264,-264h-6v240l-40,40ZM354,560h92l-44,-44 -2,-2 -46,46ZM480,367ZM402,516Z"
        )
        val ic_marked = createVector(
            "ic_marked", 12f, 12f,
            "M6,11.601C8.997,11.601 11.479,9.114 11.479,6.122C11.479,3.125 8.992,0.644 5.995,0.644C3.003,0.644 0.521,3.125 0.521,6.122C0.521,9.114 3.008,11.601 6,11.601ZM5.398,8.749C5.216,8.749 5.065,8.673 4.926,8.485L3.578,6.831C3.497,6.724 3.449,6.605 3.449,6.482C3.449,6.24 3.637,6.042 3.878,6.042C4.034,6.042 4.152,6.09 4.287,6.267L5.377,7.674L7.67,3.99C7.772,3.829 7.912,3.743 8.052,3.743C8.288,3.743 8.508,3.904 8.508,4.156C8.508,4.274 8.438,4.398 8.374,4.511L5.85,8.485C5.737,8.663 5.581,8.749 5.398,8.749Z"
        )
        val ic_delete = createVector(
            "ic_delete", 24f, 24f,
            "M15.5,4H18C18.55,4 19,4.45 19,5C19,5.55 18.55,6 18,6H6C5.45,6 5,5.55 5,5C5,4.45 5.45,4 6,4H8.5L9.21,3.29C9.39,3.11 9.65,3 9.91,3H14.09C14.35,3 14.61,3.11 14.79,3.29L15.5,4ZM8,21C6.9,21 6,20.1 6,19V9C6,7.9 6.9,7 8,7H16C17.1,7 18,7.9 18,9V19C18,20.1 17.1,21 16,21H8Z"
        )
        val ic_filter = createVector(
            "ic_filter", 40f, 40f,
            "M18.042,30q-0.584,0 -0.98,-0.396 -0.395,-0.396 -0.395,-0.979 0,-0.625 0.395,-1.021 0.396,-0.396 0.98,-0.396h3.916q0.584,0 0.98,0.417 0.395,0.417 0.395,1t-0.395,0.979q-0.396,0.396 -0.98,0.396ZM6.375,12.792q-0.583,0 -0.979,-0.417t-0.396,-1q0,-0.583 0.396,-0.979T6.375,10h27.25q0.583,0 0.979,0.396t0.396,0.979q0,0.625 -0.396,1.021 -0.396,0.396 -0.979,0.396ZM11.375,21.375q-0.583,0 -0.979,-0.396T10,20q0,-0.583 0.396,-0.979t0.979,-0.396h17.25q0.583,0 0.979,0.396T30,20q0,0.583 -0.396,0.979t-0.979,0.396Z"
        )
        val ic_ascending = createVector(
            "ic_ascending", 24f, 24f,
            "M12,23.201C17.994,23.201 22.957,18.228 22.957,12.244C22.957,6.25 17.983,1.287 11.989,1.287C6.006,1.287 1.043,6.25 1.043,12.244C1.043,18.228 6.017,23.201 12,23.201ZM12,17.787C11.517,17.787 11.162,17.443 11.162,16.949V11.439L11.259,9.086L10.152,10.418L8.842,11.739C8.691,11.89 8.477,11.986 8.251,11.986C7.789,11.986 7.445,11.632 7.445,11.181C7.445,10.944 7.51,10.751 7.66,10.601L11.356,6.916C11.581,6.69 11.764,6.604 12,6.604C12.247,6.604 12.44,6.701 12.655,6.916L16.34,10.601C16.49,10.751 16.576,10.944 16.576,11.181C16.576,11.632 16.222,11.986 15.76,11.986C15.523,11.986 15.319,11.9 15.169,11.739L13.869,10.418L12.741,9.075L12.838,11.439V16.949C12.838,17.443 12.494,17.787 12,17.787Z"
        )
        val ic_descending = createVector(
            "ic_descending", 24f, 24f,
            "M12,23.201C17.994,23.201 22.957,18.228 22.957,12.244C22.957,6.25 17.983,1.287 11.989,1.287C6.006,1.287 1.043,6.25 1.043,12.244C1.043,18.228 6.017,23.201 12,23.201ZM12,6.604C12.494,6.604 12.838,6.948 12.838,7.442V12.953L12.741,15.316L13.869,13.974L15.169,12.652C15.319,12.481 15.523,12.405 15.76,12.405C16.222,12.405 16.576,12.749 16.576,13.211C16.576,13.447 16.49,13.641 16.34,13.791L12.655,17.465C12.44,17.68 12.247,17.787 12,17.787C11.764,17.787 11.581,17.69 11.356,17.465L7.66,13.791C7.51,13.641 7.445,13.447 7.445,13.211C7.445,12.749 7.789,12.405 8.251,12.405C8.477,12.405 8.691,12.491 8.842,12.652L10.152,13.974L11.259,15.306L11.162,12.953V7.442C11.162,6.948 11.517,6.604 12,6.604Z"
        )
        val ic_settings = createVector(
            "ic_settings", 960f, 960f,
            "m370,880 l-16,-128q-13,-5 -24.5,-12T307,725l-119,50L78,585l103,-78q-1,-7 -1,-13.5v-27q0,-6.5 1,-13.5L78,375l110,-190 119,50q11,-8 23,-15t24,-12l16,-128h220l16,128q13,5 24.5,12t22.5,15l119,-50 110,190 -103,78q1,7 1,13.5v27q0,6.5 -2,13.5l103,78 -110,190 -118,-50q-11,8 -23,15t-24,12l-16,128L370,880ZM482,620q58,0 99,-41t41,-99q0,-58 -41,-99t-99,-41q-59,0 -99.5,41T342,480q0,58 40.5,99t99.5,41ZM482,540q-25,0 -42.5,-17.5T422,480q0,-25 17.5,-42.5T482,420q25,0 42.5,17.5T542,480q0,25 -17.5,42.5T482,540ZM480,480ZM440,800h79l14,-106q31,-8 57.5,-23.5T639,633l99,41 39,-68 -86,-65q5,-14 7,-29.5t2,-31.5q0,-16 -2,-31.5t-7,-29.5l86,-65 -39,-68 -99,42q-22,-23 -48.5,-38.5T533,266l-13,-106h-79l-14,106q-31,8 -57.5,23.5T321,327l-99,-41 -39,68 86,64q-5,15 -7,30t-2,32q0,16 2,31t7,30l-86,65 39,68 99,-42q22,23 48.5,38.5T427,694l13,106Z"
        )
        val ic_search = createVector(
            "ic_search", 24f, 24f,
            "M15.1862,14.4713H15.9762L20.2162,18.7313C20.6262,19.1413 20.6262,19.8113 20.2162,20.2213C19.8062,20.6313 19.1362,20.6313 18.7262,20.2213L14.4762,15.9713V15.1813L14.2062,14.9013C12.8062,16.1013 10.8962,16.7213 8.8662,16.3813C6.0862,15.9113 3.8662,13.5913 3.5262,10.7913C3.0062,6.5613 6.5662,3.0013 10.7962,3.5213C13.5962,3.8613 15.9162,6.0813 16.3862,8.8613C16.7262,10.8913 16.1062,12.8013 14.9062,14.2013L15.1862,14.4713ZM5.4762,9.9713C5.4762,12.4613 7.4862,14.4713 9.9762,14.4713C12.4662,14.4713 14.4762,12.4613 14.4762,9.9713C14.4762,7.4813 12.4662,5.4713 9.9762,5.4713C7.4862,5.4713 5.4762,7.4813 5.4762,9.9713Z"
        )
        val ic_add = createVector(
            "ic_add", 24f, 24f,
            "M18,13h-5v5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1v-5H6c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h5V6c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v5h5c0.55,0 1,0.45 1,1s-0.45,1 -1,1z"
        )
        val ic_edit_item = createVector(
            "ic_edit_item", 24f, 24f,
            "M3,17.46v3.04c0,0.28 0.22,0.5 0.5,0.5h3.04c0.13,0 0.26,-0.05 0.35,-0.15L17.81,9.94l-3.75,-3.75L3.15,17.1c-0.1,0.1 -0.15,0.22 -0.15,0.36zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
        )
        val ic_edit_note = createVector(
            "ic_edit_note", 48f, 48f,
            "M15.95,35.5h16.1v-3h-16.1ZM15.95,27h16.1v-3h-16.1ZM11,44q-1.2,0 -2.1,-0.9Q8,42.2 8,41L8,7q0,-1.2 0.9,-2.1Q9.8,4 11,4h18.05L40,14.95L40,41q0,1.2 -0.9,2.1 -0.9,0.9 -2.1,0.9ZM27.55,16.3L27.55,7L11,7v34h26L37,16.3ZM11,7v9.3L11,7v34L11,7Z"
        )
        val ic_metadata_rating = createVector(
            "ic_metadata_rating", 960f, 960f,
            "m480,691 l-166,100q-11,7 -23,6t-21,-8q-9,-7 -14,-17.5t-2,-23.5l44,-189 -147,-127q-10,-9 -12.5,-20.5T140,389q4,-11 12,-18t22,-9l194,-17 75,-178q5,-12 15.5,-18t21.5,-6q11,0 21.5,6t15.5,18l75,178 194,17q14,2 22,9t12,18q4,11 1.5,22.5T809,432L662,559l44,189q3,13 -2,23.5T690,789q-9,7 -21,8t-23,-6L480,691Z"
        )
        val ic_metadata_release = createVector(
            "ic_metadata_release", 960f, 960f,
            "M200,880q-33,0 -56.5,-23.5T120,800v-560q0,-33 23.5,-56.5T200,160h40v-40q0,-17 11.5,-28.5T280,80q17,0 28.5,11.5T320,120v40h320v-40q0,-17 11.5,-28.5T680,80q17,0 28.5,11.5T720,120v40h40q33,0 56.5,23.5T840,240v560q0,33 -23.5,56.5T760,880L200,880ZM200,800h560v-400L200,400v400Z"
        )
    }
}

class WebPreferences(private val prefix: String) : android.content.SharedPreferences {
    private val listeners = mutableListOf<android.content.SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getString(key: String, defValue: String?): String? {
        val fullKey = "${prefix}_$key"
        return localStorage[fullKey] ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return getString(key, null)?.toLongOrNull() ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return getString(key, null)?.toBooleanStrictOrNull() ?: defValue
    }

    override fun getInt(key: String, defValue: Int): Int {
        return getString(key, null)?.toIntOrNull() ?: defValue
    }

    override fun putString(key: String, value: String?) {
        val fullKey = "${prefix}_$key"
        if (value == null) {
            localStorage.removeItem(fullKey)
        } else {
            localStorage[fullKey] = value
        }
        notifyChanged(key)
    }

    override fun edit(): android.content.SharedPreferences.Editor {
        return object : android.content.SharedPreferences.Editor {
            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor = apply {
                this@WebPreferences.putString(key, value)
            }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor = apply {
                this@WebPreferences.putString(key, value.toString())
            }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor = apply {
                this@WebPreferences.putString(key, value.toString())
            }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor = apply {
                this@WebPreferences.putString(key, value.toString())
            }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged(key: String) {
        listeners.forEach { it.onSharedPreferenceChanged(this, key) }
    }
}

class WebContext : android.content.Context() {
    private val prefsMap = mutableMapOf<String, WebPreferences>()

    override fun getSharedPreferences(name: String, mode: Int): WebPreferences {
        return prefsMap.getOrPut(name) { WebPreferences(name) }
    }

    override fun startActivity(intent: android.content.Intent) {
        intent.uriString?.let { url ->
            window.open(url, "_blank")
        }
    }
}

val LocalWebContext = staticCompositionLocalOf<android.content.Context> { WebContext() }

object WindowInsetsCompat {
    val statusBars = PaddingValues(0.dp)
}

fun androidx.compose.ui.Modifier.statusBarsPadding(): androidx.compose.ui.Modifier = this
fun androidx.compose.ui.Modifier.navigationBarsPadding(): androidx.compose.ui.Modifier = this

@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier, content = content)
}

fun formatDecimal1(value: Float): String {
    val rounded = kotlin.math.round(value * 10f).toInt()
    val intPart = rounded / 10
    val decPart = kotlin.math.abs(rounded % 10)
    return "$intPart.$decPart"
}

