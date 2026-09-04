package com.sachit.moneypal.domain.model.changelog

import kotlinx.serialization.Serializable

@Serializable
data class ChangelogItem(
    val title: String,
    val type: ReleaseType,
)