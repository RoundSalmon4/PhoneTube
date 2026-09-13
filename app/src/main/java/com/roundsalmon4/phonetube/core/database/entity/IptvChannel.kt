package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_channels")
data class IptvChannel(
    @PrimaryKey val id: String,
    val providerId: String,
    val streamId: String,
    val title: String,
    val iconUrl: String,
    val categoryId: String,
    val cachedAt: Long
)