package com.github.topi314.lavasrc.amazonmusic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Amazon Music metadata from a raw JSON string.
 * This version uses manual parsing without any external JSON libraries.
 */
public class AmazonMusicParser {

	// Regex patterns to extract the artist, title and audio URL
	private static final Pattern ARTIST_PATTERN = Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern AUDIO_URL_PATTERN = Pattern.compile("\"audioUrl\"\\s*:\\s*\"(.*?)\"");
	// Regex pattern to extract the artwork URL
	private static final Pattern ARTWORK_URL_PATTERN = Pattern.compile("\"artworkUrl\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern ARTIST_NAME_PATTERN = Pattern.compile("\"artist\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"(.*?)\"");

	/**
	 * Parses a JSON string returned from Amazon Music and extracts the song title and artist.
	 * Expected JSON structure (simplified example):
	 * {
	 *   "catalog": {
	 *     "title": {
	 *       "name": "Song Name",
	 *       "artist": "Artist Name",
	 *       "audioUrl": "https://example.com/path/to/audio.mp3"
	 *     }
	 *   }
	 * }
	 *
	 * @param json the raw JSON response as a string
	 * @return formatted title in the form "Artist - Song Name" or "Unknown Title" if parsing fails
	 */
	public static String parseAmazonTitle(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return "Unknown Title";
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for title: " + json);

		String name = extractValue(NAME_PATTERN, json);
		String artist = extractValue(ARTIST_NAME_PATTERN, json); // Use the new pattern to extract artist name

		// Log extracted values
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted name: " + name);
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted artist: " + artist);

		if (name == null || artist == null) {
			System.err.println("[AmazonMusicParser] [ERROR] Failed to extract name or artist.");
			return "Unknown Title";
		}

		return artist + " - " + name;
	}

	/**
	 * Parses the audio URL from the JSON string.
	 *
	 * @param json the raw JSON response as a string
	 * @return the audio URL or null if not found or invalid
	 */
	public static String parseAudioUrl(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return null;
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for audioUrl: " + json);

		String audioUrl = extractValue(AUDIO_URL_PATTERN, json);

		// Log the extracted audioUrl
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted audioUrl: " + audioUrl);

		// Validate the audio URL format
		if (audioUrl == null || !audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav)(\\?.*)?$")) {
			System.err.println("[AmazonMusicParser] [ERROR] Invalid or unsupported audio URL: " + audioUrl);
			return null;
		}

		return audioUrl;
	}

	/**
	 * Parses the artwork URL from the JSON string.
	 *
	 * @param json the raw JSON response as a string
	 * @return the artwork URL or null if not found
	 */
	public static String parseArtworkUrl(String json) {
		if (json == null || json.isEmpty()) {
			return null;
		}
		return extractValue(ARTWORK_URL_PATTERN, json);
	}

	/**
	 * Extracts the first group matched by the given regex pattern in the provided text.
	 *
	 * @param pattern the compiled regex pattern
	 * @param text    the text to search
	 * @return the matched group or null if not found
	 */
	private static String extractValue(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}
}
