package com.lists.app.data.sync

import com.lists.app.currentTimeMillis
import com.lists.app.data.model.BackupData
import com.lists.app.data.model.ItemStatus
import com.lists.app.data.model.ListItem
import com.lists.app.data.model.Tombstone
import com.lists.app.data.model.UserList
import com.lists.app.data.store.WebStore
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import com.lists.app.fetchPackageManifest
import com.lists.app.uploadPackageToDrive
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Google Drive Sync for web — fully compatible with the Android app's JSON format.
 *
 * Android format (schemaVersion 2):
 *   {
 *     "schemaVersion": 2,
 *     "lastModified": Long,
 *     "lists": [ { "uuid", "id", "name", "category", ... } ],
 *     "items": [ { "uuid", "listId", "listUuid", "title", "status": "BACKLOG", "rating": 0.0, ... } ],
 *     "deletedLists": { "uuid": timestamp, ... },
 *     "deletedItems": { "uuid": timestamp, ... },
 *     "preferences": { "ratingScales": { "MOVIE": "skip_perfection", ... } }
 *   }
 */
object GoogleDriveSync {
    private const val CLIENT_ID = "112735802916-9b4qf1e1ru2h5gaeh184lek7uf84on5e.apps.googleusercontent.com"
    private const val SCOPES = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/userinfo.email"
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private var accessToken: String?
        get() = localStorage["lists_access_token"] ?: localStorage["listy_access_token"]
        set(value) {
            if (value != null) {
                localStorage["lists_access_token"] = value
            } else {
                localStorage.removeItem("lists_access_token")
                localStorage.removeItem("listy_access_token")
            }
        }

    fun getUserEmail(): String? = WebStore.userEmail.value
    fun getLastSyncTime(): Long? = WebStore.lastSyncTime.value
    fun setLastSyncTime(time: Long) {
        WebStore.setLastSync(time)
    }

    fun init() {
        val token = accessToken
        if (token != null) {
            fetchUserInfoAndSync(token)
        }
    }

    fun signOut() {
        accessToken = null
        WebStore.setAuth(null)
    }

    fun requestSignIn(onComplete: (Boolean) -> Unit) {
        try {
            jsRegisterTokenCallbacks(
                onSuccess = { token ->
                    accessToken = token
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            val resp = client.get("https://www.googleapis.com/oauth2/v3/userinfo") {
                                header(HttpHeaders.Authorization, "Bearer $token")
                            }
                            if (resp.status == HttpStatusCode.OK) {
                                val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                                val email = obj["email"]?.jsonPrimitive?.content
                                if (email != null) {
                                    WebStore.setAuth(email)
                                }
                            }
                        } catch (e: Exception) {
                            println("User info error: ${e.message}")
                            if (WebStore.userEmail.value == null) {
                                WebStore.setAuth("user@gmail.com")
                            }
                        }
                        syncNow()
                        onComplete(true)
                    }
                },
                onError = { err ->
                    println("Sign in failed: $err")
                    onComplete(false)
                }
            )
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
                }
                if (resp.status == HttpStatusCode.OK) {
                    val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val email = obj["email"]?.jsonPrimitive?.content
                    if (email != null) {
                        WebStore.setAuth(email)
                    }
                }
                syncNow()
            } catch (e: Exception) {
                println("User info error: ${e.message}")
            }
        }
    }

    // ==================== ANDROID FORMAT PARSING ====================

    /**
     * Parse a UserList from Android's JSON format.
     */
    private fun parseAndroidList(obj: JsonObject): UserList {
        return UserList(
            id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull ?: "",
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "List",
            description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
            category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "MOVIE",
            iconName = obj["iconName"]?.jsonPrimitive?.contentOrNull ?: "",
            colorHex = obj["colorHex"]?.jsonPrimitive?.contentOrNull ?: "",
            customOrderIndex = obj["customOrderIndex"]?.jsonPrimitive?.intOrNull ?: 0,
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: currentTimeMillis(),
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: currentTimeMillis(),
            sortingOption = obj["sortingOption"]?.jsonPrimitive?.contentOrNull ?: "DATE_ADDED_DESC",
            filterOption = obj["filterOption"]?.jsonPrimitive?.contentOrNull ?: "ALL",
            viewMode = obj["viewMode"]?.jsonPrimitive?.contentOrNull ?: "GRID_2"
        )
    }

    /**
     * Parse a ListItem from Android's JSON format.
     */
    private fun parseAndroidItem(obj: JsonObject): ListItem {
        val statusStr = obj["status"]?.jsonPrimitive?.contentOrNull ?: "BACKLOG"
        val status = try { ItemStatus.valueOf(statusStr) } catch (_: Exception) { ItemStatus.BACKLOG }

        return ListItem(
            id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull ?: "",
            listId = obj["listId"]?.jsonPrimitive?.longOrNull ?: 0L,
            listUuid = obj["listUuid"]?.jsonPrimitive?.contentOrNull ?: "",
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled",
            originalTitle = obj["originalTitle"]?.jsonPrimitive?.contentOrNull ?: "",
            overview = obj["overview"]?.jsonPrimitive?.contentOrNull ?: "",
            creator = obj["creator"]?.jsonPrimitive?.contentOrNull ?: "",
            coverUrl = obj["coverUrl"]?.jsonPrimitive?.contentOrNull ?: "",
            backdropUrl = obj["backdropUrl"]?.jsonPrimitive?.contentOrNull ?: "",
            releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull ?: "",
            releaseYear = obj["releaseYear"]?.jsonPrimitive?.contentOrNull ?: "",
            rating = obj["rating"]?.jsonPrimitive?.floatOrNull ?: 0f,
            userRating = obj["userRating"]?.jsonPrimitive?.floatOrNull ?: 0f,
            status = status,
            marked = obj["marked"]?.jsonPrimitive?.booleanOrNull ?: false,
            markedAt = obj["markedAt"]?.jsonPrimitive?.longOrNull,
            pinned = obj["pinned"]?.jsonPrimitive?.booleanOrNull ?: false,
            pinnedAt = obj["pinnedAt"]?.jsonPrimitive?.longOrNull,
            userNotes = obj["userNotes"]?.jsonPrimitive?.contentOrNull ?: "",
            externalUrl = obj["externalUrl"]?.jsonPrimitive?.contentOrNull ?: "",
            category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "MOVIE",
            currentSeason = obj["currentSeason"]?.jsonPrimitive?.intOrNull ?: 1,
            currentEpisode = obj["currentEpisode"]?.jsonPrimitive?.intOrNull ?: 0,
            totalEpisodes = obj["totalEpisodes"]?.jsonPrimitive?.intOrNull ?: 0,
            currentPage = obj["currentPage"]?.jsonPrimitive?.intOrNull ?: 0,
            totalPages = obj["totalPages"]?.jsonPrimitive?.intOrNull ?: 0,
            gamePlatform = obj["gamePlatform"]?.jsonPrimitive?.contentOrNull ?: "",
            customOrder = obj["customOrder"]?.jsonPrimitive?.intOrNull ?: 0,
            reviewRating = obj["reviewRating"]?.jsonPrimitive?.intOrNull ?: 0,
            checkedEpisodesJson = obj["checkedEpisodesJson"]?.jsonPrimitive?.contentOrNull ?: "",
            episodesDataJson = obj["episodesDataJson"]?.jsonPrimitive?.contentOrNull ?: "",
            createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: currentTimeMillis(),
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: currentTimeMillis()
        )
    }

    /**
     * Parse Android's tombstone format: { "uuid": timestamp, ... } objects
     * into our Tombstone list.
     */
    private fun parseAndroidTombstones(
        deletedListsObj: JsonObject?,
        deletedItemsObj: JsonObject?
    ): List<Tombstone> {
        val tombstones = mutableListOf<Tombstone>()
        deletedListsObj?.forEach { (uuid, ts) ->
            tombstones.add(Tombstone(
                entityUuid = uuid,
                entityType = "LIST",
                deletedAt = ts.jsonPrimitive.longOrNull ?: 0L
            ))
        }
        deletedItemsObj?.forEach { (uuid, ts) ->
            tombstones.add(Tombstone(
                entityUuid = uuid,
                entityType = "ITEM",
                deletedAt = ts.jsonPrimitive.longOrNull ?: 0L
            ))
        }
        return tombstones
    }

    /**
     * Parse remote JSON that could be either Android format or web format.
     * Returns a BackupData object.
     */
    private fun parseRemoteJson(remoteJsonStr: String): BackupData? {
        return try {
            val root = json.parseToJsonElement(remoteJsonStr).jsonObject

            // Detect format: Android has "schemaVersion", web has "version"
            val isAndroidFormat = root.containsKey("schemaVersion") ||
                    root.containsKey("deletedLists") ||
                    root.containsKey("deletedItems")

            if (isAndroidFormat) {
                println("Sync: Detected Android JSON format")
                val listsArr = root["lists"]?.jsonArray ?: JsonArray(emptyList())
                val itemsArr = root["items"]?.jsonArray ?: JsonArray(emptyList())
                val deletedListsObj = root["deletedLists"]?.jsonObject
                val deletedItemsObj = root["deletedItems"]?.jsonObject

                val lists = listsArr.map { parseAndroidList(it.jsonObject) }
                val items = itemsArr.map { parseAndroidItem(it.jsonObject) }
                val tombstones = parseAndroidTombstones(deletedListsObj, deletedItemsObj)

                // Import rating preferences if present
                val prefsObj = root["preferences"]?.jsonObject
                val ratingScales = prefsObj?.get("ratingScales")?.jsonObject
                if (ratingScales != null) {
                    val currentPrefs = WebStore.ratingPrefs.value.toMutableMap()
                    ratingScales.forEach { (key, value) ->
                        val scaleId = value.jsonPrimitive.contentOrNull ?: return@forEach
                        currentPrefs["rating_scale_$key"] = scaleId
                    }
                    // Store in localStorage
                    ratingScales.forEach { (key, value) ->
                        val scaleId = value.jsonPrimitive.contentOrNull ?: return@forEach
                        localStorage["lists_rating_prefs_rating_scale_$key"] = scaleId
                    }
                }

                BackupData(
                    version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 2,
                    exportedAt = root["lastModified"]?.jsonPrimitive?.longOrNull ?: currentTimeMillis(),
                    lists = lists,
                    items = items,
                    tombstones = tombstones
                )
            } else {
                // Web/BackupData format — try kotlinx deserialization
                println("Sync: Detected web JSON format")
                json.decodeFromString<BackupData>(remoteJsonStr)
            }
        } catch (e: Exception) {
            println("Sync: Failed to parse remote JSON: ${e.message}")
            null
        }
    }

    // ==================== ANDROID FORMAT EXPORT ====================

    /**
     * Export a UserList to Android-compatible JSON.
     */
    private fun listToAndroidJson(list: UserList): JsonObject {
        return buildJsonObject {
            put("uuid", list.uuid)
            put("id", list.id)
            put("name", list.name)
            put("description", list.description)
            put("category", list.category)
            put("iconName", list.iconName)
            put("colorHex", list.colorHex)
            put("customOrderIndex", list.customOrderIndex)
            put("sortingOption", list.sortingOption)
            put("filterOption", list.filterOption)
            put("viewMode", list.viewMode)
            put("createdAt", list.createdAt)
            put("updatedAt", list.updatedAt)
        }
    }

    /**
     * Export a ListItem to Android-compatible JSON.
     */
    private fun itemToAndroidJson(item: ListItem, lists: List<UserList>): JsonObject {
        // Resolve listUuid from parent list
        val parentList = lists.firstOrNull { it.id == item.listId }
        val listUuid = if (item.listUuid.isNotEmpty()) item.listUuid else (parentList?.uuid ?: "")

        return buildJsonObject {
            put("id", item.id)
            put("uuid", item.uuid)
            put("listId", item.listId)
            put("listUuid", listUuid)
            put("title", item.title)
            put("originalTitle", item.originalTitle)
            put("overview", item.overview)
            put("creator", item.creator)
            put("coverUrl", item.coverUrl)
            put("backdropUrl", item.backdropUrl)
            put("releaseDate", item.releaseDate)
            put("releaseYear", item.releaseYear)
            put("rating", item.rating.toDouble())
            put("userRating", item.userRating.toDouble())
            put("status", item.status.name)
            put("marked", item.marked)
            if (item.markedAt != null) put("markedAt", item.markedAt) else put("markedAt", JsonNull)
            put("pinned", item.pinned)
            if (item.pinnedAt != null) put("pinnedAt", item.pinnedAt) else put("pinnedAt", JsonNull)
            put("userNotes", item.userNotes)
            put("externalUrl", item.externalUrl)
            put("category", item.category)
            put("currentSeason", item.currentSeason)
            put("currentEpisode", item.currentEpisode)
            put("totalEpisodes", item.totalEpisodes)
            put("currentPage", item.currentPage)
            put("totalPages", item.totalPages)
            put("gamePlatform", item.gamePlatform)
            put("customOrder", item.customOrder)
            put("reviewRating", item.reviewRating)
            put("checkedEpisodesJson", item.checkedEpisodesJson)
            put("episodesDataJson", item.episodesDataJson)
            put("createdAt", item.createdAt)
            put("updatedAt", item.updatedAt)
        }
    }

    /**
     * Export merged data as Android-compatible JSON string.
     */
    private fun exportAndroidFormat(backup: BackupData): String {
        val root = buildJsonObject {
            put("schemaVersion", 2)
            put("lastModified", backup.exportedAt)

            // Lists
            putJsonArray("lists") {
                backup.lists.forEach { add(listToAndroidJson(it)) }
            }

            // Items
            putJsonArray("items") {
                backup.items.forEach { add(itemToAndroidJson(it, backup.lists)) }
            }

            // Tombstones in Android format: separate objects
            putJsonObject("deletedLists") {
                backup.tombstones.filter { it.entityType == "LIST" }.forEach { t ->
                    put(t.entityUuid, t.deletedAt)
                }
            }
            putJsonObject("deletedItems") {
                backup.tombstones.filter { it.entityType == "ITEM" }.forEach { t ->
                    put(t.entityUuid, t.deletedAt)
                }
            }

            // Preferences (rating scales from localStorage)
            putJsonObject("preferences") {
                putJsonObject("ratingScales") {
                    listOf("MOVIE", "TV_SHOW", "BOOK", "GAME", "ANIME", "MANGA").forEach { catId ->
                        val scale = localStorage["lists_rating_prefs_rating_scale_$catId"]
                        if (scale != null) {
                            put(catId, scale)
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    // ==================== SYNC ====================

    /**
     * Check if the current token is valid by making a lightweight request.
     * Returns true if token works, false if expired/invalid.
     */
    private suspend fun isTokenValid(token: String): Boolean {
        return try {
            val resp = client.get("https://www.googleapis.com/oauth2/v1/tokeninfo") {
                parameter("access_token", token)
            }
            resp.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Request a fresh token using GIS with a 10s timeout to prevent hanging. Returns new token or null.
     */
    private suspend fun refreshTokenSilently(): String? {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(10000L) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    jsRegisterTokenCallbacks(
                        onSuccess = { newToken ->
                            accessToken = newToken
                            if (cont.isActive) cont.resumeWith(Result.success(newToken))
                        },
                        onError = { err ->
                            println("Sync: Token refresh error: $err")
                            if (cont.isActive) cont.resumeWith(Result.success(null))
                        }
                    )
                    jsInitTokenClient(CLIENT_ID, SCOPES)
                }
            }
        } catch (e: Exception) {
            println("Sync: Token refresh timeout/exception: ${e.message}")
            null
        }
    }

    suspend fun syncNow(): Boolean {
        var token = accessToken
        if (token == null) {
            println("Sync: No access token stored. Attempting interactive token acquisition...")
            token = refreshTokenSilently()
            if (token == null) {
                println("Sync: User not signed in and token not obtainable.")
                return false
            }
        }

        return try {
            println("Sync: Starting sync...")

            // Step 1: Validate the token first
            if (!isTokenValid(token)) {
                println("Sync: Token expired or invalid. Requesting fresh token...")
                val newToken = refreshTokenSilently()
                if (newToken != null) {
                    token = newToken
                    println("Sync: Got fresh token successfully")
                } else {
                    println("Sync: Could not refresh token. User may need to sign in again.")
                    return false
                }
            }

            // Step 2: Search for sync files using clean Ktor parameters
            val searchResp = client.get("https://www.googleapis.com/drive/v3/files") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("spaces", "appDataFolder")
                parameter("q", "(name='lists_sync.listspkg' or name='listy_sync.listypkg' or name='lists_sync.json' or name='listy_sync.json' or name='lists_backup.json' or name='listy_backup.json') and trashed=false")
                parameter("fields", "files(id,name,modifiedTime)")
                parameter("pageSize", 10)
            }

            // Check for auth errors on search
            if (searchResp.status == HttpStatusCode.Unauthorized) {
                println("Sync: 401 on file search. Token invalid. Clearing token.")
                accessToken = null
                return false
            }
            if (searchResp.status != HttpStatusCode.OK) {
                println("Sync: File search failed with HTTP ${searchResp.status}")
                return false
            }

            val searchBody = searchResp.bodyAsText()
            val searchObj = json.parseToJsonElement(searchBody).jsonObject

            // Check for error object in response
            if (searchObj.containsKey("error")) {
                val errorMsg = searchObj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown"
                println("Sync: API error on search: $errorMsg")
                return false
            }

            val files = searchObj["files"]?.jsonArray
            println("Sync: Found ${files?.size ?: 0} sync file(s) on Drive")

            // Pick file with latest modifiedTime
            val existingFile = files?.map { it.jsonObject }?.maxByOrNull { f ->
                f["modifiedTime"]?.jsonPrimitive?.contentOrNull ?: ""
            }
            val fileId = existingFile?.get("id")?.jsonPrimitive?.content
            val fileName = existingFile?.get("name")?.jsonPrimitive?.content ?: "lists_sync.json"
            val isPackage = fileName.endsWith(".listspkg") || fileName.endsWith(".listypkg") || fileName.endsWith(".zip")

            if (fileId != null) {
                val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                println("Sync: Downloading remote file $fileName ($fileId, isPackage=$isPackage)")

                // Step 3: Download remote file
                val remoteJsonStr: String? = if (isPackage) {
                    try {
                        fetchPackageManifest(downloadUrl, token)
                    } catch (e: Exception) {
                        println("Sync: Failed to unpack package $fileName: ${e.message}")
                        null
                    }
                } else {
                    val downloadResp = client.get(downloadUrl) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    if (downloadResp.status == HttpStatusCode.OK) {
                        downloadResp.bodyAsText()
                    } else {
                        println("Sync: Download failed with HTTP ${downloadResp.status}")
                        null
                    }
                }

                if (remoteJsonStr != null) {
                    println("Sync: Downloaded ${remoteJsonStr.length} bytes, parsing...")
                    val remoteBackup = parseRemoteJson(remoteJsonStr)
                    if (remoteBackup == null) {
                        println("Sync: Could not parse remote data — aborting to prevent data loss")
                        return false
                    }

                    val remoteItemCount = remoteBackup.items.size
                    val localItemCount = WebStore.items.value.size
                    println("Sync: Remote has ${remoteBackup.lists.size} lists, $remoteItemCount items. Local has ${WebStore.lists.value.size} lists, $localItemCount items.")

                    // Step 4: Merge local + remote
                    val mergedBackup = WebStore.mergeFromRemote(remoteBackup)
                    println("Sync: Merged result: ${mergedBackup.lists.size} lists, ${mergedBackup.items.size} items")

                    // Safety check: never upload if merged has fewer items than remote
                    if (mergedBackup.items.size < remoteItemCount) {
                        println("Sync WARNING: Merged (${mergedBackup.items.size} items) has fewer items than remote ($remoteItemCount). Skipping upload to prevent data loss.")
                        WebStore.setLastSync(currentTimeMillis())
                        return true
                    }

                    // Step 5: Upload merged result
                    val mergedJson = exportAndroidFormat(mergedBackup)
                    val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"

                    if (isPackage) {
                        println("Sync: Uploading merged data as package...")
                        val uploaded = uploadPackageToDrive(uploadUrl, token, mergedJson)
                        if (!uploaded) {
                            println("Sync: Upload package failed")
                            return false
                        }
                    } else {
                        println("Sync: Uploading merged data as JSON...")
                        val uploadResp = client.patch(uploadUrl) {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            header(HttpHeaders.ContentType, "application/json")
                            setBody(mergedJson)
                        }
                        if (uploadResp.status != HttpStatusCode.OK) {
                            println("Sync: Upload failed with HTTP ${uploadResp.status}")
                            return false
                        }
                    }
                    println("Sync: Upload complete (${mergedBackup.lists.size} lists, ${mergedBackup.items.size} items)")
                } else {
                    println("Sync: Could not download remote data, skipping upload")
                }
            } else {
                // No remote file — create initial upload
                println("Sync: No remote file found. Creating initial upload...")
                val localBackup = WebStore.createBackup()

                // Only upload if we have data to upload
                if (localBackup.lists.isEmpty() && localBackup.items.isEmpty()) {
                    println("Sync: No local data and no remote file. Nothing to do.")
                    return true
                }

                val localJson = exportAndroidFormat(localBackup)

                val boundary = "foo_bar_baz"
                val delimiter = "\r\n--$boundary\r\n"
                val closeDelimiter = "\r\n--$boundary--"

                val metadata = "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                        """{"name": "lists_sync.json", "parents": ["appDataFolder"]}"""

                val multipartBody = delimiter +
                        metadata +
                        delimiter +
                        "Content-Type: application/json\r\n\r\n" +
                        localJson +
                        closeDelimiter

                val createUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                val createResp = client.post(createUrl) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("Content-Type", "multipart/related; boundary=\"$boundary\"")
                    setBody(multipartBody)
                }
                if (createResp.status != HttpStatusCode.OK) {
                    println("Sync: Initial upload failed with HTTP ${createResp.status}")
                    return false
                }
                println("Sync: Initial upload complete (${localBackup.lists.size} lists, ${localBackup.items.size} items)")
            }
            WebStore.setLastSync(currentTimeMillis())
            println("Sync: ✅ Sync completed successfully")
            true
        } catch (e: Exception) {
            println("Sync FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

private fun jsInitTokenClient(clientId: String, scopes: String): Unit = js("""{
    if (typeof google !== 'undefined' && google.accounts && google.accounts.oauth2) {
        var tokenClient = google.accounts.oauth2.initTokenClient({
            client_id: clientId,
            scope: scopes,
            error_callback: function(err) {
                console.error("GIS token error:", err);
                if (window.__onGoogleTokenError) {
                    window.__onGoogleTokenError(err && err.message ? err.message : "Auth error");
                }
            },
            callback: function(resp) {
                if (resp && resp.access_token) {
                    if (window.__onGoogleToken) {
                        window.__onGoogleToken(resp.access_token);
                    }
                } else {
                    console.error("GIS response missing access_token", resp);
                    if (window.__onGoogleTokenError) {
                        window.__onGoogleTokenError(resp && resp.error ? resp.error : "No access token");
                    }
                }
            }
        });
        tokenClient.requestAccessToken();
    } else {
        console.error("Google Identity Services not loaded");
        if (window.__onGoogleTokenError) {
            window.__onGoogleTokenError("Google Identity Services not loaded");
        }
    }
}""")

private fun jsRegisterTokenCallbacks(onSuccess: (String) -> Unit, onError: (String) -> Unit): Unit = js("""{
    window.__onGoogleToken = onSuccess;
    window.__onGoogleTokenError = onError;
}""")

