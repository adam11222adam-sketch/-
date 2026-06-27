package com.example.data.repositories

import com.example.data.local.SavedOrderDao
import com.example.data.models.SavedOrder
import kotlinx.coroutines.flow.Flow

class OrderRepository(private val savedOrderDao: SavedOrderDao) {
    val allSavedOrders: Flow<List<SavedOrder>> = savedOrderDao.getAllSavedOrders()

    suspend fun insert(order: SavedOrder) = savedOrderDao.insertSavedOrder(order)

    suspend fun getLatestOrderWithPrefix(prefix: String): SavedOrder? =
        savedOrderDao.getLatestOrderWithPrefix(prefix)

    suspend fun deleteById(id: String) = savedOrderDao.deleteSavedOrderById(id)
}
