package com.roundsalmon4.phonetube.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_providers")
data class IptvProvider(
    @PrimaryKey val id: String,
    val host: String,
    val username: String,
    val password: String,
    val name: String,
    val scheme: String = "https",
    val timezone: String = "",
    val enabled: Boolean = true
) {
    companion object {
        /**
         * Stable identifier used to build playable ids (iptv:<id>:<streamId>)
         * and to look credentials back up when the player starts a stream.
         */
        fun makeId(host: String, username: String): String = "$host|$username"
    }
}