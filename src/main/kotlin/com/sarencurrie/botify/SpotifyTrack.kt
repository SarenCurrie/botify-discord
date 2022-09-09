package com.sarencurrie.botify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTrack(
    val album: SpotifyAlbum,
    val artists: List<SpotifyArtist>,
    val id: String,
    val name: String,
    val external_urls: SpotifyExternalUrl,
)

@Serializable
data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>,
    val external_urls: SpotifyExternalUrl,
)

@Serializable
data class SpotifyImage(
    val height: Int,
    val width: Int,
    val url: String,
)

@Serializable
class SpotifyArtist(
    val name: String,
    val id: String,
    val external_urls: SpotifyExternalUrl,
)

@Serializable
class SpotifyExternalUrl(
    val spotify: String
)

