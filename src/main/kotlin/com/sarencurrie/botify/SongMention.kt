package com.sarencurrie.botify

import java.time.Instant

data class SongMention(
    val guildId: String,
    val title: String,
    val spotifyId: String,
    val primaryArtist: String,
    val artists: List<Pair<String, String>>,
    val album: String,
    val albumId: String,
    val mentioner: String,
    val channel: String,
    val messageId: String,
    val timestamp: Instant,
)