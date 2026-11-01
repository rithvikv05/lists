package android.widget

class Toast {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Any?, message: String, duration: Int): Toast {
            println("[Toast] $message")
            return Toast()
        }
    }
    fun show() {}
}
