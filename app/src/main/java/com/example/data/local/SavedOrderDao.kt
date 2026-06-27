package com.example.data.local

import androidx.room.*
import com.example.data.models.SavedOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedOrderDao {
    @Query("SELECT * FROM saved_orders ORDER BY createdAt DESC")
    fun getAllSavedOrders(): Flow<List<SavedOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedOrder(order: SavedOrder)

    @Query("SELECT * FROM saved_orders WHERE id LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLatestOrderWithPrefix(prefix: String): SavedOrder?

    @Query("DELETE FROM saved_orders WHERE id = :id")
    suspend fun deleteSavedOrderById(id: String)
}
