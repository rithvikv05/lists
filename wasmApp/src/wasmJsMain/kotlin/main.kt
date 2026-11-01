import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.lists.app.data.model.ListItem
import com.lists.app.data.model.UserList
import com.lists.app.data.store.WebStore
import com.lists.app.data.sync.GoogleDriveSync
import com.lists.app.ui.screens.*
import com.lists.app.ui.theme.ListyTheme
import com.lists.app.ui.theme.PureBlack
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Screen {
    object Lists : Screen()
    data class ListDetail(val userList: UserList) : Screen()
    data class AddItem(val userList: UserList) : Screen()
    data class ItemDetail(val item: ListItem) : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    try {
        WebStore.init()
    } catch (e: Throwable) {
        println("WebStore.init error: ${e.message}")
    }
    try {
        GoogleDriveSync.init()
    } catch (e: Throwable) {
        println("GoogleDriveSync.init error: ${e.message}")
    }

    val body = document.body ?: run {
        println("ERROR: document.body is null")
        return
    }

    ComposeViewport(body) {
        ListyTheme {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Lists) }
            val coroutineScope = rememberCoroutineScope()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack)
            ) {
                when (val screen = currentScreen) {
                    is Screen.Lists -> {
                        ListsScreen(
                            onOpenList = { userList ->
                                currentScreen = Screen.ListDetail(userList)
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
                            },
                            onSyncClick = {
                                coroutineScope.launch {
                                    GoogleDriveSync.syncNow()
                                }
                            }
                        )
                    }

                    is Screen.ListDetail -> {
                        ListDetailScreen(
                            userList = screen.userList,
                            onBack = {
                                currentScreen = Screen.Lists
                            },
                            onAddItem = {
                                currentScreen = Screen.AddItem(screen.userList)
                            },
                            onOpenItem = { item ->
                                currentScreen = Screen.ItemDetail(item)
                            }
                        )
                    }

                    is Screen.AddItem -> {
                        AddItemScreen(
                            userList = screen.userList,
                            onBack = {
                                currentScreen = Screen.ListDetail(screen.userList)
                            },
                            onItemAdded = {
                                currentScreen = Screen.ListDetail(screen.userList)
                            }
                        )
                    }

                    is Screen.ItemDetail -> {
                        ItemDetailScreen(
                            item = screen.item,
                            onBack = {
                                val lists = WebStore.lists.value
                                val parentList = lists.firstOrNull { it.id == screen.item.listId || it.uuid == screen.item.listUuid }
                                if (parentList != null) {
                                    currentScreen = Screen.ListDetail(parentList)
                                } else {
                                    currentScreen = Screen.Lists
                                }
                            }
                        )
                    }

                    is Screen.Settings -> {
                        SettingsScreen(
                            onBack = {
                                currentScreen = Screen.Lists
                            },
                            onConnectDrive = {
                                GoogleDriveSync.requestSignIn { }
                            },
                            onSyncNow = {
                                coroutineScope.launch {
                                    GoogleDriveSync.syncNow()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
