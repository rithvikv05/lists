package com.lists.app.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Handles Google Sign-In with drive.appdata scope for Drive sync.
 */
class GoogleSignInHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInHelper"
    }

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Returns the currently signed-in account, or null if not signed in.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            account
        } else {
            null
        }
    }

    /**
     * Returns an Intent to launch the Google Sign-In UI.
     * Use with ActivityResultLauncher.
     */
    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    /**
     * Sign out the current user.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            signInClient.signOut().await()
            Log.d(TAG, "Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }

    /**
     * Revoke access (disconnect the app from Google account).
     */
    suspend fun revokeAccess() = withContext(Dispatchers.IO) {
        try {
            signInClient.revokeAccess().await()
            Log.d(TAG, "Access revoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Revoke access failed", e)
        }
    }
}
