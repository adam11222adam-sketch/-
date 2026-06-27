package com.example.data.repositories

import com.example.data.local.CartItemDao
import com.example.data.models.CartItem
import kotlinx.coroutines.flow.Flow

class CartRepository(private val cartItemDao: CartItemDao) {
    val allCartItems: Flow<List<CartItem>> = cartItemDao.getAllCartItems()

    suspend fun getCartItemsList(): List<CartItem> = cartItemDao.getCartItemsList()

    suspend fun insert(item: CartItem) = cartItemDao.insertCartItem(item)

    suspend fun update(item: CartItem) = cartItemDao.updateCartItem(item)

    suspend fun deleteById(id: String) = cartItemDao.deleteCartItemById(id)

    suspend fun clearCart() = cartItemDao.clearCart()
}
