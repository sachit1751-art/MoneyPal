package com.sachit.moneypal.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isHidden: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional emoji shown as the category avatar; null = default styling. */
    @ColumnInfo(defaultValue = "NULL")
    val emoji: String? = null,
    /** ARGB hex color (e.g. "FFE91E63") used for the category avatar/chip; null = theme default. */
    @ColumnInfo(defaultValue = "NULL")
    val colorArgb: String? = null,
    /** Optional monthly envelope limit for this category; null = unbounded. Stored as plain string (money never floats). */
    @ColumnInfo(defaultValue = "NULL")
    val monthlyLimit: String? = null
)