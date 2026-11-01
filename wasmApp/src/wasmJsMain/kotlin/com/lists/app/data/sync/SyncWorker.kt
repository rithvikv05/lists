package com.lists.app.data.sync

object SyncWorker {
    fun triggerOneTimeSync(context: Any? = null) {}
    fun schedulePeriodicSync(context: Any? = null, wifiOnly: Boolean = false) {}
    fun cancelPeriodicSync(context: Any? = null) {}
}
