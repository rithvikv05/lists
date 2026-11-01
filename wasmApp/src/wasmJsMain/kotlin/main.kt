import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.lists.app.LocalWebContext
import com.lists.app.WebContext
import com.lists.app.data.repository.AppRepository
import com.lists.app.data.store.WebStore
import com.lists.app.data.sync.GoogleDriveSync
import com.lists.app.ui.screens.*
import com.lists.app.ui.theme.DarkBorder
import com.lists.app.ui.theme.ListsTheme
import com.lists.app.ui.theme.PureBlack
import kotlinx.browser.document
import kotlinx.browser.window

sealed class Screen {
    object Lists : Screen()
    data class ListDetail(val listId: Long) : Screen()
    data class AddItem(val listId: Long) : Screen()
    data class ItemDetail(val itemId: Long) : Screen()
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

    val webContext = WebContext()
    val repository = AppRepository(webContext)

    ComposeViewport(body) {
        CompositionLocalProvider(LocalWebContext provides webContext) {
            ListsTheme(darkTheme = true) {
                val backStack = remember { mutableStateListOf<Screen>(Screen.Lists) }
                val currentScreen = backStack.lastOrNull() ?: Screen.Lists

                // Full width responsive container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) {
                    when (currentScreen) {
                            is Screen.Lists -> {
                                ListsScreen(
                                    repository = repository,
                                    onNavigateToListDetail = { listId ->
                                        backStack.add(Screen.ListDetail(listId))
                                    },
                                    onNavigateToSettings = {
                                        backStack.add(Screen.Settings)
                                    }
                                )
                            }

                            is Screen.ListDetail -> {
                                ListDetailScreen(
                                    listId = currentScreen.listId,
                                    repository = repository,
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    },
                                    onNavigateToItemDetail = { itemId ->
                                        backStack.add(Screen.ItemDetail(itemId))
                                    },
                                    onNavigateToAddItem = { listId ->
                                        backStack.add(Screen.AddItem(listId))
                                    }
                                )
                            }

                            is Screen.AddItem -> {
                                AddItemScreen(
                                    listId = currentScreen.listId,
                                    repository = repository,
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }

                            is Screen.ItemDetail -> {
                                ItemDetailScreen(
                                    itemId = currentScreen.itemId,
                                    repository = repository,
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }

                            is Screen.Settings -> {
                                SettingsScreen(
                                    repository = repository,
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}


