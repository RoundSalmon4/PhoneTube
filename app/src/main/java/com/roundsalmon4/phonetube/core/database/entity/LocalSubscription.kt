package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [Index("channelId", unique = true)]
)
data class LocalSubscription(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val subscribedAt: Long
)
