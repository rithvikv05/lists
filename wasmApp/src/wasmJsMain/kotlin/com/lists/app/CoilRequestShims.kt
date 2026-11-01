package coil.request

class ImageRequest(val data: Any?) {
    class Builder(val context: Any?) {
        private var dataVal: Any? = null
        fun data(data: Any?) = apply { this.dataVal = data }
        fun crossfade(enable: Boolean) = apply { }
        fun build(): ImageRequest = ImageRequest(dataVal)
    }
}
