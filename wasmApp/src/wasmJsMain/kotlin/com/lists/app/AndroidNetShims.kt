package android.net

data class Uri(val value: String) {
    override fun toString(): String = value
    companion object {
        fun parse(url: String): Uri = Uri(url)
    }
}
