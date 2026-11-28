package com.minimal.lists.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import com.minimal.lists.data.model.RatingScaleOption
import sh.calvin.reorderable.*

@Composable
fun ManualItemLayout(
    listId: Long,
    allItems: List<ListItem>,
    visibleItems: List<ListItem>,
    grid: Boolean,
    columns: Int,
    category: Category,
    scale: RatingScaleOption,
    onOpen: (Long) -> Unit,
    onToggle: (ListItem) -> Unit,
    onSaveOrder: (List<Long>) -> Unit
) {
    fun storedOrder() = allItems.sortedWith(compareBy<ListItem> { if (it.customOrder > 0) it.customOrder else Int.MAX_VALUE }
        .thenByDescending { it.createdAt }.thenBy { it.id }).map { it.id }
    var order by remember(listId) { mutableStateOf(storedOrder()) }
    var dragging by remember(listId) { mutableStateOf(false) }
    LaunchedEffect(listId, allItems.map { it.id to it.customOrder }) {
        if (!dragging) order = storedOrder()
    }
    val visibleIds = visibleItems.map { it.id }.toSet()
    val byId = allItems.associateBy { it.id }
    val displayed = order.mapNotNull { id -> byId[id]?.takeIf { id in visibleIds } }
    fun move(from: Any, to: Any) {
        val fromIndex = order.indexOf(from)
        val toIndex = order.indexOf(to)
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    fun finish() {
        dragging = false
        onSaveOrder(order.toList())
    }
    if (grid) {
        val state = rememberLazyGridState()
        val reorder = rememberReorderableLazyGridState(state) { from, to -> move(from.key, to.key) }
        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = state, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(displayed, key = { it.id }) { item ->
                ReorderableItem(reorder, key = item.id) { active ->
                    GridMediaCard(item = item, category = category, scale = scale,
                        onClick = { if (!dragging) onOpen(item.id) }, onToggleComplete = { onToggle(item) },
                        modifier = Modifier.longPressDraggableHandle(onDragStarted = { dragging = true }, onDragStopped = { finish() })
                            .shadow(if (active) 8.dp else 0.dp))
                }
            }
        }
    } else {
        val state = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(state) { from, to -> move(from.key, to.key) }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayed, key = { it.id }) { item ->
                ReorderableItem(reorder, key = item.id) { active ->
                    ListMediaRow(item = item, category = category, scale = scale,
                        onClick = { if (!dragging) onOpen(item.id) }, onToggleComplete = { onToggle(item) },
                        modifier = Modifier.longPressDraggableHandle(onDragStarted = { dragging = true }, onDragStopped = { finish() })
                            .shadow(if (active) 8.dp else 0.dp))
                }
            }
        }
    }
}
