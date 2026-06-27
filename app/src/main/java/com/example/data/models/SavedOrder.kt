package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_orders")
data class SavedOrder(
    @PrimaryKey val id: String, // e.g. SH-20260607-0001
    val createdAt: Long = System.currentTimeMillis(),
    val customerName: String,
    val phone: String,
    val city: String,
    val address: String,
    val generalNotes: String? = null,
    val itemsCount: Int,
    val totalQuantity: Int,
    val estimatedTotal: Double,
    val status: String = "تم الإرسال عبر واتساب",
    val itemsJson: String // Serialized CartItem list
)
