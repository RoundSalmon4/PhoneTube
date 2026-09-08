package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_favorites")
data class IptvFavorite(
    @PrimaryKey val videoId: String,
    val title: String,
    val providerName: String,
    val iconUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)