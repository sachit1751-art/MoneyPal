package com.sachit.moneypal.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val isHidden: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional emoji avatar; null = default styling. */
    val emoji: String? = null,
    /** ARGB hex color for the avatar/chip; null = theme default. */
    val colorArgb: String? = null
) {
    companion object {
        fun create(
            name: String,
            isHidden: Boolean = false,
            usageCount: Int = 0,
            lastUsedAt: Long? = null,
            createdAt: Long = System.currentTimeMillis(),
            emoji: String? = null,
            colorArgb: String? = null
        ): Category = Category(
            id = 0,
            name = name,
            isHidden = isHidden,
            usageCount = usageCount,
            lastUsedAt = lastUsedAt,
            createdAt = createdAt,
            emoji = emoji,
            colorArgb = colorArgb
        )
    }
}