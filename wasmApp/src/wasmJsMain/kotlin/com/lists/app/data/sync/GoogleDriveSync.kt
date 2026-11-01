package com.lists.app.data.sync

import com.lists.app.data.model.BackupData
import com.lists.app.data.store.WebStore
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.w3c.dom.get
import org.w3c.dom.set

object GoogleDriveSync {
    private const val CLIENT_ID = "112735802916-8cpc9959u76m3mj578rcouj3ng17kero.apps.googleusercontent.com"
    private const val SCOPES = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/userinfo.email"
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private var accessToken: String?
        get() = localStorage["listy_access_token"]
        set(value) {
            if (value != null) localStorage["listy_access_token"] = value else localStorage.removeItem("listy_access_token")
        }

    fun init() {
        val token = accessToken
        if (token != null) {
            fetchUserInfoAndSync(token)
        }
    }

    fun requestSignIn(onComplete: (Boolean) -> Unit) {
        try {
            jsRegisterTokenCallback { token ->
                accessToken = token
                fetchUserInfoAndSync(token)
                onComplete(true)
            }
            jsInitTokenClient(CLIENT_ID, SCOPES)
        } catch (e: Exception) {
            println("Sign in exception: ${e.message}")
            onComplete(false)
        }
    }

    private fun fetchUserInfoAndSync(token: String) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val resp = client.get("https://www.googleapis.com/oauth2/v3/userinfo") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.bodyAsText()
                val obj = json.parseToJsonElement(resp).jsonObject
                val email = obj["email"]?.jsonPrimitive?.content
                if (email != null) {
                    WebStore.setAuth(email)
                }
                syncNow()
            } catch (e: Exception) {
                println("User info error: ${e.message}")
            }
        }
    }

    suspend fun syncNow(): Boolean {
        val token = accessToken ?: return false

        return try {
            // 1. Search for existing listy_backup.json in appDataFolder
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='listy_backup.json'%20and%20trashed=false&fields=files(id,name,modifiedTime)"
            val searchResp = client.get(searchUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.bodyAsText()

            val searchObj = json.parseToJsonElement(searchResp).jsonObject
            val files = searchObj["files"]?.jsonArray
            val existingFile = files?.firstOrNull()?.jsonObject
            val fileId = existingFile?.get("id")?.jsonPrimitive?.content

            if (fileId != null) {
                // Download remote backup
                val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                val downloadResp = client.get(downloadUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                if (downloadResp.status == HttpStatusCode.OK) {
                    val remoteJson = downloadResp.bodyAsText()
                    try {
                        val remoteBackup = json.decodeFromString<BackupData>(remoteJson)
                        // Merge bidirectional
                        val mergedBackup = WebStore.mergeFromRemote(remoteBackup)
                        val mergedJson = json.encodeToString(BackupData.serializer(), mergedBackup)

                        // Upload updated merged content
                        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                        client.patch(uploadUrl) {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody(mergedJson)
                        }
                    } catch (e: Exception) {
                        println("Failed to parse remote backup: ${e.message}")
                    }
                }
            } else {
                // Upload initial local backup
                val localJson = WebStore.exportBackupJson()
                val metadata = """{"name": "listy_backup.json", "parents": ["appDataFolder"]}"""
                val boundary = "-------314159265358979323846"
                val delimiter = "\r\n--$boundary\r\n"
                val closeDelimiter = "\r\n--$boundary--"

                val multipartBody = delimiter +
                        "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        metadata +
                        delimiter +
                        "Content-Type: application/json\r\n\r\n" +
                        localJson +
                        closeDelimiter

                val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                client.post(createUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Content-Type", "multipart/related; boundary=\"$boundary\"")
                    setBody(multipartBody)
                }
            }
            true
        } catch (e: Exception) {
            println("Sync failed: ${e.message}")
            false
        }
    }
}

private fun jsInitTokenClient(clientId: String, scopes: String): Unit = js("""{
    if (typeof google !== 'undefined' && google.accounts && google.accounts.oauth2) {
        var tokenClient = google.accounts.oauth2.initTokenClient({
            client_id: clientId,
            scope: scopes,
            callback: function(resp) {
                if (resp && resp.access_token) {
                    if (window.__onGoogleToken) {
                        window.__onGoogleToken(resp.access_token);
                    }
                }
            }
        });
        tokenClient.requestAccessToken();
    } else {
        console.error("Google Identity Services not loaded");
    }
}""")

private fun jsRegisterTokenCallback(callback: (String) -> Unit): Unit = js("""{
    window.__onGoogleToken = callback;
}""")

