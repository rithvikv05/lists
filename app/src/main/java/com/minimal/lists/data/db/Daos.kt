package com.minimal.lists.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ListWithStats(
    @Embedded val list: UserList,
    val itemCount: Int,
    val completedCount: Int
)

@Dao
interface ListDao {
    @Query("""
        SELECT 
            lists.*,
            (SELECT COUNT(*) FROM items WHERE items.listId = lists.id) AS itemCount,
            (SELECT COUNT(*) FROM items WHERE items.listId = lists.id AND items.marked = 1) AS completedCount
        FROM lists
        ORDER BY lists.customOrderIndex ASC, lists.id ASC
    """)
    fun getAllListsWithStats(): Flow<List<ListWithStats>>

    @Query("SELECT * FROM lists WHERE id = :id LIMIT 1")
    fun getListById(id: Long): Flow<UserList?>

    @Query("SELECT * FROM lists WHERE id = :id LIMIT 1")
    suspend fun getListByIdSync(id: Long): UserList?

    @Query("SELECT * FROM lists WHERE uuid = :uuid LIMIT 1")
    suspend fun getListByUuid(uuid: String): UserList?

    @Query("SELECT * FROM lists ORDER BY customOrderIndex ASC, id ASC")
    suspend fun getAllListsSync(): List<UserList>

    @Query("SELECT MAX(customOrderIndex) FROM lists")
    suspend fun getMaxOrderIndex(): Int?

    @Query("SELECT MIN(customOrderIndex) FROM lists")
    suspend fun getMinOrderIndex(): Int?

    @Query("UPDATE lists SET customOrderIndex = :orderIndex WHERE id = :id")
    suspend fun updateCustomOrder(id: Long, orderIndex: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: UserList): Long

    @Update
    suspend fun updateList(list: UserList)

    @Update
    suspend fun updateLists(lists: List<UserList>)

    @Delete
    suspend fun deleteList(list: UserList)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteListById(id: Long)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY pinned DESC, id DESC")
    fun getItemsForList(listId: Long): Flow<List<ListItem>>

    @Query("SELECT * FROM items WHERE listId = :listId ORDER BY pinned DESC, id DESC")
    suspend fun getItemsForListSync(listId: Long): List<ListItem>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun getItemById(id: Long): Flow<ListItem?>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemByIdSync(id: Long): ListItem?

    @Query("SELECT * FROM items WHERE uuid = :uuid LIMIT 1")
    suspend fun getItemByUuid(uuid: String): ListItem?

    @Query("SELECT coverUrl FROM items WHERE listId = :listId AND coverUrl != '' ORDER BY id DESC LIMIT 4")
    fun getPreviewCovers(listId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ListItem>)

    @Update
    suspend fun updateItem(item: ListItem)

    @Delete
    suspend fun deleteItem(item: ListItem)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM items WHERE listId = :listId AND marked = 1")
    suspend fun clearCompleted(listId: Long)

    @Query("SELECT * FROM items")
    suspend fun getAllItemsSync(): List<ListItem>
}

