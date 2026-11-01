package android.content

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getLong(key: String, defValue: Long): Long
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getInt(key: String, defValue: Int): Int
    fun putString(key: String, value: String?)
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putLong(key: String, value: Long): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun apply()
        fun commit(): Boolean
    }

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?)
    }
}

object Context {
    const val MODE_PRIVATE = 0
}

class Intent(val action: String? = null, val uri: Any? = null) {
    val uriString: String? = (uri as? android.net.Uri)?.value ?: uri?.toString()
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
    }
}
