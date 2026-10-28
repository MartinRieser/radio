package com.pulsecast.radio.playback

import com.pulsecast.radio.model.Station

data class FormattedRadioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val appTrackTitle: String
)

/**
 * Parses raw ICY stream metadata and formats it specifically for car Bluetooth AVRCP displays
 * (such as Subaru Starlink 3-line screens: Title / Artist / Album) and in-app display.
 */
object RadioMetadataFormatter {

    private val SEPARATOR_REGEX = Regex("\\s+[-–—]\\s+")

    fun format(
        rawTitle: String?,
        station: Station?
    ): FormattedRadioMetadata {
        val stationName = station?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "PulseCast Radio"
        val fallbackSubtitle = station?.country?.trim()?.takeIf { it.isNotEmpty() } ?: "Live Radio"

        var clean = (rawTitle ?: "")
            .trim()
            .removeSuffix(";")
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        // Strip station name if prepended as prefix (e.g. "SWR3 - Queen - Radio Ga Ga")
        if (clean.startsWith("$stationName -", ignoreCase = true)) {
            clean = clean.substringAfter("-").trim()
        } else if (clean.startsWith("$stationName:", ignoreCase = true)) {
            clean = clean.substringAfter(":").trim()
        }

        val parts = if (clean.contains(SEPARATOR_REGEX)) {
            clean.split(SEPARATOR_REGEX, limit = 2)
        } else {
            emptyList()
        }

        return if (parts.size == 2) {
            val part1 = parts[0].trim()
            val part2 = parts[1].trim()

            if (part1.equals(stationName, ignoreCase = true)) {
                // e.g. "SWR3 - Popnacht"
                val song = part2.ifBlank { stationName }
                FormattedRadioMetadata(
                    title = song,
                    artist = stationName,
                    album = fallbackSubtitle,
                    appTrackTitle = song
                )
            } else if (part2.equals(stationName, ignoreCase = true)) {
                // e.g. "Popnacht - SWR3"
                val song = part1.ifBlank { stationName }
                FormattedRadioMetadata(
                    title = song,
                    artist = stationName,
                    album = fallbackSubtitle,
                    appTrackTitle = song
                )
            } else {
                // e.g. "Queen - Bohemian Rhapsody"
                val songArtist = part1.ifBlank { fallbackSubtitle }
                val songTitle = part2.ifBlank { stationName }
                FormattedRadioMetadata(
                    title = songTitle,
                    artist = songArtist,
                    album = "📻 $stationName",
                    appTrackTitle = "$songArtist - $songTitle"
                )
            }
        } else if (clean.isNotBlank() && !clean.equals(stationName, ignoreCase = true)) {
            // Single text (e.g. "Nachrichten um 8")
            FormattedRadioMetadata(
                title = clean,
                artist = stationName,
                album = fallbackSubtitle,
                appTrackTitle = clean
            )
        } else {
            // No song info / idle -> Title is Station Name, Artist is Country/Live Radio, Album is PulseCast Radio
            FormattedRadioMetadata(
                title = stationName,
                artist = fallbackSubtitle,
                album = "PulseCast Radio",
                appTrackTitle = ""
            )
        }
    }
}

