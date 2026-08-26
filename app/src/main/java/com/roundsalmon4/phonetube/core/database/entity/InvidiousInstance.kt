package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invidious_instances")
data class InvidiousInstance(
    @PrimaryKey val host: String,
    val name: String = host,
    val enabled: Boolean = true
)
