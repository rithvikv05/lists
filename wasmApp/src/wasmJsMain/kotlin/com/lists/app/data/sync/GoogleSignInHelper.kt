package com.lists.app.data.sync

data class GoogleSignInAccount(val email: String, val displayName: String = "") {
    val account: Any? = null
}

class GoogleSignInHelper(val context: Any? = null) {
    fun getSignedInAccount(): GoogleSignInAccount? {
        val email = GoogleDriveSync.getUserEmail() ?: return null
        return GoogleSignInAccount(email, email)
    }

    fun requestSignIn(onComplete: (Boolean) -> Unit) {
        GoogleDriveSync.requestSignIn(onComplete)
    }

    suspend fun signOut() {
        GoogleDriveSync.signOut()
    }
}
