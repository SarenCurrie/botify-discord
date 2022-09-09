@file:OptIn(ExperimentalSerializationApi::class)

package com.sarencurrie.botify

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Instant
import java.util.*
import java.util.function.Supplier

val json = Json { ignoreUnknownKeys = true }
val mentions = mutableListOf<SongMention>()

fun main() {
    val client = JDABuilder
        .create(
            System.getenv("BOTIFY_DISCORD_TOKEN"),
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.DIRECT_MESSAGES
        )
        .disableCache(
            CacheFlag.ACTIVITY,
            CacheFlag.CLIENT_STATUS,
            CacheFlag.EMOTE,
            CacheFlag.MEMBER_OVERRIDES,
            CacheFlag.VOICE_STATE
        )
        .addEventListeners(CommandListener(Sqlite()))
        .build()
    client.awaitReady()
    client.guilds.forEach{
        it.upsertCommand("analyse-channel", "Searches this channel's history for Spotify links")
            .setDefaultEnabled(true)
            .queue()
        it.upsertCommand("song-stats", "Shares some stats about previously posted songs")
            .setDefaultEnabled(true)
            .queue()
    }
}

class CommandListener(private val sqlite: Sqlite): ListenerAdapter() {
    private val slashCommands = mapOf<String, (SlashCommandEvent) -> Any>(
        "analyse-channel" to fun(event) {
            event.deferReply().complete()
            println("/analyse-channel called")
            val spotifyLink = Regex("https://open\\.spotify\\.com/track/(\\w+)")
            val history = event.channel.history
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            history.retrievePast(100).complete()
            val posts = history.retrievedHistory
            println("Found ${posts.size} posts. This might take a while...")
            event.hook.sendMessage("Found ${posts.size} posts. This might take a while...").complete()
            val result = posts.flatMap { spotifyLink.findAll(it.contentRaw).map { match -> Pair(it, match.groupValues[1]) } }
                .map { Pair(it.first, callSpotifyApi(it.second)) }
                .map { val artists = it.second.artists.map { artist -> Pair(artist.name, artist.id) }
                    SongMention(
                        guildId = it.first.guild.id,
                        title = it.second.name,
                        spotifyId = it.second.id,
                        primaryArtist = artists[0].first,
                        artists = artists,
                        album = it.second.album.name,
                        albumId = it.second.album.id,
                        mentioner = it.first.author.id,
                        channel = it.first.channel.id,
                        messageId = it.first.id,
                        timestamp = it.first.timeCreated.toInstant()
                ) }
            println("Loaded message history for #${event.channel.name}")
            mentions.addAll(result)
            mentions.forEach {
                sqlite.save(it)
            }
            event.hook.sendMessage("Loaded message history for #${event.channel.name}").complete()
        },
        "song-stats" to fun(event) {
            println("/song-stats called")
            if (event.guild == null) {
                event.reply("Error, must be called in server").complete()
                return
            }
            event.deferReply().complete()
            val e = EmbedBuilder()
            e.setTitle("Spotify Stats for #${event.channel.name}")
            val songCounts = sqlite.getUniqueSongCount(event.guild!!.id, event.channel.id)
            e.addField("Total songs mentioned", songCounts.first.toString(), true)
            e.addField("Unique songs mentioned", songCounts.second.toString(), true)
            e.addBlankField(true)
            val artistCounts = sqlite.getUniqueArtistCount(event.guild!!.id, event.channel.id)
            e.addField("Total artists mentioned", artistCounts.first.toString(), true)
            e.addField("Unique artists mentioned", artistCounts.second.toString(), true)
            e.addBlankField(true)
            val popularSongs = sqlite.findMostMentionedSongs(event.guild!!.id, event.channel.id, 5)
            val topList = popularSongs.map {
                "${it.first} - ${it.second} (${it.third})"
            }.reduce { acc, s -> "$acc\n$s" }
            e.addField("Most mentioned songs", topList, false)
            val popularArtists = sqlite.findMostMentionedArtists(event.guild!!.id, event.channel.id, 5)
            val artistList = popularArtists.map {
                "${it.first} (${it.second})"
            }.reduce { acc, s -> "$acc\n$s" }
            e.addField("Most mentioned artists", artistList, false)
            event.hook.sendMessageEmbeds(e.build()).complete()
        }
    )

    override fun onSlashCommand(event: SlashCommandEvent) {
        slashCommands[event.name]?.invoke(event)
    }
}

val token: Supplier<String> = object : Supplier<String> {
    var cachedToken: String? = null
    var expiry: Instant = Instant.now()
    override fun get(): String {
        val callTime = Instant.now()
        if (expiry.isAfter(callTime) && cachedToken != null) {
            return cachedToken!!
        }
        val basicAuth = Base64.getEncoder().encodeToString("${System.getenv("SPOTIFY_CLIENT_ID")}:${System.getenv("SPOTIFY_CLIENT_SECRET")}".toByteArray())
        val bodyPublisher = BodyPublishers.ofString("grant_type=client_credentials")
        val request = HttpRequest.newBuilder(URI("https://accounts.spotify.com/api/token"))
            .POST(bodyPublisher)
            .header("Authorization", "Basic $basicAuth")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .build()
        val response = HttpClient.newBuilder()
            .build().send(request, BodyHandlers.ofString())
        val token = json.decodeFromString(SpotifyToken.serializer(), response.body())
        cachedToken = token.access_token
        expiry = callTime.plusSeconds(token.expires_in.toLong())
        return token.access_token
    }
}

@Serializable
data class SpotifyToken(
    val access_token: String,
    val expires_in: Int,
)

fun callSpotifyApi(trackId: String): SpotifyTrack {
    val request = HttpRequest.newBuilder(URI("https://api.spotify.com/v1/tracks/$trackId?market=NZ"))
        .GET()
        .header("Authorization", "Bearer ${token.get()}")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .build()
    val response = HttpClient.newBuilder()
        .build().send(request, BodyHandlers.ofInputStream())

    return json.decodeFromStream(response.body())
}