package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productUrl: String,
    val productName: String? = null,
    val size: String,
    val color: String,
    val quantity: Int = 1,
    val displayedPrice: Double? = null,
    val notes: String? = null,
    val localImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
