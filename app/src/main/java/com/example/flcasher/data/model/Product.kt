package com.example.flcasher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Int,
    val imageUri: String?, // Local file path on server
    val isAvailable: Boolean = true
)
