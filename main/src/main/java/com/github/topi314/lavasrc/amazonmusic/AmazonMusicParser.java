package com.github.topi314.lavasrc.amazonmusic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Amazon Music metadata from a raw JSON string.
 * This version uses manual parsing without any external JSON libraries.
 */
public class AmazonMusicParser {

	// Regex patterns to extract the artist and title
	private static final Pattern ARTIST_PATTERN = Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"(.*?)\"");

	/**
	 * Parses a JSON string returned from Amazon Music and extracts the song title and artist.
	 * Expected JSON structure:
	 * {
	 *   "catalog": {
	 *     "title": {
	 *       "name": "Song Name",
	 *       "artist": "Artist Name"
	 *     }
	 *   }
	 * }
	 *
	 * @param json the raw JSON response as a string
	 * @return formatted title in the form "Artist - Song Name" or "Unknown Title" if parsing fails
	 */
	public static String parseAmazonTitle(String json) {
		if (json == null || json.isEmpty()) {
			return "Unknown Title";
		}

		String name = extractValue(NAME_PATTERN, json);
		String artist = extractValue(ARTIST_PATTERN, json);

		if (name == null || artist == null) {
			return "Unknown Title";
		}

		return artist + " - " + name;
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
