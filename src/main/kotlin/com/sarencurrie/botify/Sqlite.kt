package com.sarencurrie.botify

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.util.*

class Sqlite : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:botify.sqlite")

    init {
        connection.createStatement().execute("PRAGMA foreign_keys = ON")
        connection.createStatement()
            .executeUpdate("""CREATE TABLE IF NOT EXISTS SongMentions (
                id TEXT PRIMARY KEY,
                guildId TEXT,
                title TEXT,
                spotifyId TEXT,
                primaryArtist TEXT,
                displayArtist TEXT,
                album TEXT,
                albumId TEXT,
                mentioner TEXT,
                channel TEXT,
                messageId TEXT,
                timestamp INT,
                UNIQUE (messageId, spotifyId) ON CONFLICT IGNORE)""".trimMargin())
        connection.createStatement()
            .executeUpdate("""CREATE TABLE IF NOT EXISTS SongArtists (
                id TEXT PRIMARY KEY,
                mentionId TEXT,
                name TEXT,
                spotifyId TEXT,
                guildId TEXT,
                channel TEXT,
                FOREIGN KEY(mentionId) REFERENCES SongMentions(id),
                UNIQUE (mentionId, spotifyId) ON CONFLICT REPLACE)""".trimMargin())
        connection.createStatement()
            .execute("CREATE INDEX IF NOT EXISTS guild_channel ON SongMentions (guildId, channel)")
        connection.createStatement()
            .execute("CREATE INDEX IF NOT EXISTS guild_channel2 ON SongArtists (guildId, channel)")
    }

    @Synchronized
    fun save(mention: SongMention) {
        connection.autoCommit = false
        val statement = connection.prepareStatement("INSERT INTO SongMentions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        val mentionId = UUID.randomUUID().toString()
        statement.setString(1, mentionId)
        statement.setString(2, mention.guildId)
        statement.setString(3, mention.title)
        statement.setString(4, mention.spotifyId)
        statement.setString(5, mention.primaryArtist)
        statement.setString(6, mention.artists.map { it.first }.reduce { acc, s -> "$acc & $s" })
        statement.setString(7, mention.album)
        statement.setString(8, mention.albumId)
        statement.setString(9, mention.mentioner)
        statement.setString(10, mention.channel)
        statement.setTimestamp(11, Timestamp.from(mention.timestamp))
        val wasUpdated = statement.executeUpdate() > 0
        if (wasUpdated) {
            val artistStatement = connection.prepareStatement("INSERT INTO SongArtists VALUES(?, ?, ?, ?, ?, ?)")
            for (artist in mention.artists) {
                artistStatement.setString(1, UUID.randomUUID().toString())
                artistStatement.setString(2, mentionId)
                artistStatement.setString(3, artist.first)
                artistStatement.setString(4, artist.second)
                artistStatement.setString(5, mention.guildId) // Screw Boyce-Codd normal form
                artistStatement.setString(6, mention.channel)
                artistStatement.execute()
            }
        }
        connection.commit()
    }

    fun findMostMentionedSongs(guild: String, channel: String, count: Int): MutableList<Triple<String, String, Int>> {
        val statement = connection.prepareStatement("""
            SELECT title, displayArtist, COUNT(spotifyId) AS mentionCount 
            FROM SongMentions
            WHERE guildId = ?
            AND channel = ?
            GROUP BY spotifyId
            ORDER BY mentionCount DESC
            LIMIT ?
        """.trimIndent())
        statement.setString(1, guild)
        statement.setString(2, channel)
        statement.setInt(3, count)
        val results = statement.executeQuery()
        val topSongs = mutableListOf<Triple<String, String, Int>>()
        while (results.next()) {
            topSongs.add(Triple(results.getString(1), results.getString(2), results.getInt(3)))
        }
        return topSongs
    }

    fun findMostMentionedArtists(guild: String, channel: String, count: Int): MutableList<Pair<String, Int>> {
        val statement = connection.prepareStatement("""
            SELECT name, COUNT(spotifyId) AS mentionCount 
            FROM SongArtists
            WHERE guildId = ?
            AND channel = ?
            GROUP BY spotifyId
            ORDER BY mentionCount DESC
            LIMIT ?
        """.trimIndent())
        statement.setString(1, guild)
        statement.setString(2, channel)
        statement.setInt(3, count)
        val results = statement.executeQuery()
        val topArtists = mutableListOf<Pair<String, Int>>()
        while (results.next()) {
            topArtists.add(Pair(results.getString(1), results.getInt(2)))
        }
        return topArtists
    }

    fun getUniqueSongCount(guild: String, channel: String): Pair<Int, Int> {
        val statement = connection.prepareStatement("""
            SELECT COUNT(spotifyId) AS total, COUNT(distinct spotifyId) AS totalUnique
            FROM SongMentions
            WHERE guildId = ?
            AND channel = ?
        """.trimIndent())
        statement.setString(1, guild)
        statement.setString(2, channel)
        val results = statement.executeQuery()
        results.next()
        return Pair(results.getInt(1), results.getInt(2))
    }

    fun getUniqueArtistCount(guild: String, channel: String): Pair<Int, Int> {
        val statement = connection.prepareStatement("""
            SELECT COUNT(spotifyId) AS total, COUNT(distinct spotifyId) AS totalUnique
            FROM SongArtists
            WHERE guildId = ?
            AND channel = ?
        """.trimIndent())
        statement.setString(1, guild)
        statement.setString(2, channel)
        val results = statement.executeQuery()
        results.next()
        return Pair(results.getInt(1), results.getInt(2))
    }

    override fun close() {
        connection.close()
    }
}