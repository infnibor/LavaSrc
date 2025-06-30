package com.github.topi314.lavasrc.amazonmusic;

import com.github.topi314.lavasrc.amazonmusic.AmazonMusicParser;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;

public class AmazonMusicSourceManager implements AudioSourceManager {
    private static final String AMAZON_MUSIC_URL_REGEX =
        "https?:\\/\\/music\\.amazon\\.[a-z.]+\\/(tracks|albums|playlists|artists)\\/([A-Za-z0-9]+)";
    private static final Pattern AMAZON_MUSIC_URL_PATTERN = Pattern.compile(AMAZON_MUSIC_URL_REGEX);

    private final String apiUrl;
    private final String apiKey;

    public AmazonMusicSourceManager(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public AmazonMusicSourceManager(String apiUrl) {
        this(apiUrl, null);
    }

    @Override
    public String getSourceName() {
        return "amazonmusic";
    }

    public AudioItem loadItem(com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager manager, AudioReference reference) {
        Matcher matcher = AMAZON_MUSIC_URL_PATTERN.matcher(reference.identifier);
        if (!matcher.matches()) {
            return null;
        }
        String type = matcher.group(1);
        String id = matcher.group(2);

        id = id.split("[^a-zA-Z0-9]", 2)[0];

        String trackAsin = reference.identifier.contains("?") ? extractQueryParam(reference.identifier, "trackAsin") : null;

        String queryParams = reference.identifier.contains("?") ? reference.identifier.split("\\?", 2)[1] : null;

        try {
            // Handle album with trackAsin (single track from album)
            if ("albums".equals(type) && trackAsin != null) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                TrackJson foundTrack = null;
                for (TrackJson track : albumJson.tracks) {
                    if (trackAsin.equals(track.id) || trackAsin.equals(extractJsonString(trackToJson(track), "asin", null))) {
                        foundTrack = track;
                        break;
                    }
                }
                if (foundTrack == null) {
                    System.err.println("[AmazonMusic] [ERROR] No track with id/asIn=" + trackAsin + " found in album " + id);
                    return null;
                }
                // If audioUrl is null, fetch from /stream_urls endpoint
                if (foundTrack.audioUrl == null) {
                    foundTrack.audioUrl = fetchAudioUrlFromStreamUrls(foundTrack.id != null ? foundTrack.id : trackAsin);
                }
                if (foundTrack.audioUrl == null) {
                    System.err.println("[AmazonMusic] [ERROR] audioUrl is still null after stream_urls for track: " + foundTrack.id);
                    return null;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    foundTrack.title,
                    foundTrack.artist,
                    foundTrack.duration,
                    foundTrack.id != null ? foundTrack.id : "",
                    false,
                    reference.identifier
                );
                return new AmazonMusicAudioTrack(info, foundTrack.audioUrl, this);
            // Handle single track
            } else if ("tracks".equals(type)) {
                String trackId = id;
                TrackJson trackJson = fetchTrackInfo(trackId);
                if (trackJson == null) return null;

                // Fetch artwork URL
                String artworkUrl = AmazonMusicParser.parseArtworkUrl(trackToJson(trackJson));

                if (trackJson.audioUrl == null) {
                    trackJson.audioUrl = fetchAudioUrlFromStreamUrls(trackId);
                }
                if (trackJson.audioUrl == null) {
                    System.err.println("[AmazonMusic] [ERROR] audioUrl is still null after stream_urls for track: " + trackId);
                    return null;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    trackJson.title,
                    trackJson.artist,
                    trackJson.duration,
                    trackId,
                    false,
                    reference.identifier
                );
                AmazonMusicAudioTrack track = new AmazonMusicAudioTrack(info, trackJson.audioUrl, this);
                track.setArtworkUrl(artworkUrl);
                return track;
            // Handle full album
            } else if ("albums".equals(type)) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new java.util.ArrayList<>();
                for (TrackJson track : albumJson.tracks) {
                    String audioUrl = track.audioUrl;
                    // If audioUrl is null, fetch from /stream_urls endpoint
                    if (audioUrl == null) {
                        audioUrl = fetchAudioUrlFromStreamUrls(track.id);
                    }
                    if (audioUrl == null) continue;

                    // Fetch artwork URL
                    String artworkUrl = AmazonMusicParser.parseArtworkUrl(trackToJson(track));

                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    AmazonMusicAudioTrack audioTrack = new AmazonMusicAudioTrack(info, audioUrl, this);
                    audioTrack.setArtworkUrl(artworkUrl);
                    tracks.add(audioTrack);
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(albumJson.title != null ? albumJson.title : "Amazon Music Album", tracks, null, false);
            // Handle playlist
            } else if ("playlists".equals(type)) {
                PlaylistJson playlistJson = fetchPlaylistInfo(id);
                if (playlistJson == null || playlistJson.tracks == null || playlistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new java.util.ArrayList<>();
                for (TrackJson track : playlistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    // If audioUrl is null, fetch from /stream_urls endpoint
                    if (audioUrl == null) {
                        audioUrl = fetchAudioUrlFromStreamUrls(track.id);
                    }
                    if (audioUrl == null) continue;
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(playlistJson.title != null ? playlistJson.title : "Amazon Music Playlist", tracks, null, false);
            // Handle artist (top tracks)
            } else if ("artists".equals(type)) {
                ArtistJson artistJson = fetchArtistInfo(id);
                if (artistJson == null || artistJson.tracks == null || artistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new java.util.ArrayList<>();
                for (TrackJson track : artistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    // If audioUrl is null, fetch from /stream_urls endpoint
                    if (audioUrl == null) {
                        audioUrl = fetchAudioUrlFromStreamUrls(track.id);
                    }
                    if (audioUrl == null) continue;
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(artistJson.name != null ? artistJson.name : "Amazon Music Artist", tracks, null, false);
            }
            return null;
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Amazon Music item", FriendlyException.Severity.FAULT, e);
        }
    }

    /**
     * Searches for tracks on Amazon Music.
     *
     * @param query The search query.
     * @param limit The maximum number of results to return.
     * @return List of found AudioTrack objects (never null).
     */
    public java.util.List<AudioTrack> search(String query, int limit) {
        try {
            return searchTracks(query, limit);
        } catch (Exception e) {
            throw new FriendlyException("Failed to search Amazon Music: " + e.getMessage(), FriendlyException.Severity.COMMON, e);
        }
    }

    private List<AudioTrack> searchTracks(String query, int limit) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "search?query=" + encode(query) : apiUrl + "/search?query=" + encode(query);
        if (limit > 0) url += "&limit=" + limit;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return new ArrayList<>();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();
        // Parse JSON array of tracks (minimal parser)
        List<AudioTrack> tracks = new ArrayList<>();
        int idx = json.indexOf("[");
        int end = json.indexOf("]", idx);
        if (idx == -1 || end == -1) return tracks;
        String arr = json.substring(idx + 1, end);
        String[] items = arr.split("\\},\\{");
        for (String item : items) {
            String obj = item;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            TrackJson t = new TrackJson();
            t.title = extractJsonString(obj, "title", "Unknown Title");
            t.artist = extractJsonString(obj, "artist", "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            if (t.audioUrl == null || !isSupportedAudioFormat(t.audioUrl)) continue;
            AudioTrackInfo info = new AudioTrackInfo(
                t.title,
                t.artist,
                t.duration,
                t.id != null ? t.id : "",
                false,
                t.audioUrl
            );
            tracks.add(new AmazonMusicAudioTrack(info, t.audioUrl, this));
        }
        return tracks;
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return s;
        }
    }

    private static class AlbumJson {
        String title;
        TrackJson[] tracks;
    }
    private static class PlaylistJson {
        String title;
        TrackJson[] tracks;
    }
    private static class ArtistJson {
        String name;
        TrackJson[] tracks;
    }

    private AlbumJson fetchAlbumInfo(String albumId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "album?id=" + albumId : apiUrl + "/album?id=" + albumId;
        return fetchTracksContainer(url, AlbumJson.class);
    }
    private PlaylistJson fetchPlaylistInfo(String playlistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "playlist?id=" + playlistId : apiUrl + "/playlist?id=" + playlistId;
        return fetchTracksContainer(url, PlaylistJson.class);
    }
    private ArtistJson fetchArtistInfo(String artistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "artist?id=" + artistId : apiUrl + "/artist?id=" + artistId;
        return fetchTracksContainer(url, ArtistJson.class);
    }

    private <T> T fetchTracksContainer(String url, Class<T> clazz) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();

        if (clazz == AlbumJson.class) {
            AlbumJson result = new AlbumJson();
            result.title = extractJsonString(json, "title", "Amazon Music Album");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        } else if (clazz == PlaylistJson.class) {
            PlaylistJson result = new PlaylistJson();
            result.title = extractJsonString(json, "title", "Amazon Music Playlist");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        } else if (clazz == ArtistJson.class) {
            ArtistJson result = new ArtistJson();
            result.name = extractJsonString(json, "name", "Amazon Music Artist");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        }
        return null;
    }

    private TrackJson[] extractTracksArray(String json) {
        int idx = json.indexOf("\"tracks\"");
        if (idx == -1) return new TrackJson[0];
        int start = json.indexOf('[', idx);
        int end = json.indexOf(']', start);
        if (start == -1 || end == -1) return new TrackJson[0];
        String arr = json.substring(start + 1, end);
        String[] items = arr.split("\\},\\{");
        java.util.List<TrackJson> tracks = new java.util.ArrayList<>();
        for (String item : items) {
            String obj = item;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            TrackJson t = new TrackJson();
            t.title = extractJsonString(obj, "title", "Unknown Title");
            t.artist = extractJsonString(obj, "artist", "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            tracks.add(t);
        }
        return tracks.toArray(new TrackJson[0]);
    }

    private static class TrackJson {
        String id;
        String title;
        String artist;
        long duration;
        String audioUrl;
    }

    private TrackJson fetchTrackInfo(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "track?id=" + trackId : apiUrl + "/track?id=" + trackId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();
        TrackJson result = new TrackJson();
        result.title = extractJsonString(json, "title", "Unknown Title");
        result.artist = extractJsonString(json, "artist", "Unknown Artist");
        result.duration = extractJsonLong(json, "duration", 0L);
        result.audioUrl = extractJsonString(json, "audioUrl", null);
        result.id = extractJsonString(json, "id", null);
        return result;
    }

    private static String extractJsonString(String json, String key, String def) {
        return AmazonMusicParser.parseAmazonTitle(json);
    }

    private static long extractJsonLong(String json, String key, long def) {
        String regex = "\"" + key + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : def;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        if (track instanceof AmazonMusicAudioTrack) {
            ((AmazonMusicAudioTrack) track).encode(output);
        } else {
            throw new IllegalArgumentException("Unsupported track type for encoding.");
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return AmazonMusicAudioTrack.decode(trackInfo, input, this);
    }

    @Override
    public void shutdown() {
    }

	/**
	 * Fetches audioUrl from /stream_urls?id={track_id} endpoint.
	 */
	private String fetchAudioUrlFromStreamUrls(String trackId) throws IOException {
		if (trackId == null) {
			System.err.println("[AmazonMusic] [ERROR] Track ID is null.");
			return null;
		}

		String url = apiUrl.endsWith("/") ? apiUrl + "stream_urls?id=" + trackId : apiUrl + "/stream_urls?id=" + trackId;
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		if (apiKey != null && !apiKey.isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + apiKey);
		}

		int status = conn.getResponseCode();
		InputStream inputStream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
		StringBuilder content = new StringBuilder();
		String line;
		while ((line = in.readLine()) != null) {
			content.append(line);
		}
		in.close();
		conn.disconnect();

		String json = content.toString();
		if (status != 200) {
			System.err.println("[AmazonMusic] [ERROR] Failed to fetch stream_urls for track: " + trackId);
			System.err.println("Response: " + json);
			return null;
		}

		// Searching for the "urls" object: { ... }
		String audioUrl = null;
		java.util.regex.Matcher urlsMatcher = java.util.regex.Pattern.compile("\"urls\"\\s*:\\s*\\{(.*?)\\}").matcher(json);
		if (urlsMatcher.find()) {
			String urlsContent = urlsMatcher.group(1);
			audioUrl = extractJsonString(urlsContent, "high");
			if (audioUrl == null) audioUrl = extractJsonString(urlsContent, "medium");
			if (audioUrl == null) audioUrl = extractJsonString(urlsContent, "low");
		}

		// If not found in "urls", try "audioUrl" directly
		if (audioUrl == null) {
			audioUrl = extractJsonString(json, "audioUrl");
		}

		// Log error if audioUrl is still null
		if (audioUrl == null || audioUrl.isEmpty()) {
			System.err.println("[AmazonMusic] [ERROR] audioUrl is still null after processing stream_urls for track: " + trackId);
			throw new IllegalStateException("Missing audioUrl for Amazon Music track: " + trackId);
		}

		return audioUrl;
	}

	/**
	 * Extracts a JSON string value for a given key using regex (no JSON parser used).
	 */
	private String extractJsonString(String json, String key) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"").matcher(json);
		return matcher.find() ? matcher.group(1) : null;
	}


    // Helper to convert TrackJson to JSON string for asin extraction if needed
    private String trackToJson(TrackJson track) {
        StringBuilder sb = new StringBuilder("{");
        if (track.id != null) sb.append("\"id\":\"").append(track.id).append("\",");
        if (track.title != null) sb.append("\"title\":\"").append(track.title).append("\",");
        if (track.artist != null) sb.append("\"artist\":\"").append(track.artist).append("\",");
        sb.append("\"duration\":").append(track.duration).append(",");
        if (track.audioUrl != null) sb.append("\"audioUrl\":\"").append(track.audioUrl).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static boolean isSupportedAudioFormat(String audioUrl) {
        return audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav)(\\?.*)?$");
    }

    public void playTrackFromJson(String json) {
        String title = AmazonMusicParser.parseAmazonTitle(json);
        String audioUrl = AmazonMusicParser.parseAudioUrl(json);

        if (audioUrl == null || !isSupportedAudioFormat(audioUrl)) {
            System.err.println("No valid audio URL found or unsupported file format, cannot play track");
        } else {
            System.out.println("Track details:");
            System.out.println("Title: " + title);
            System.out.println("Audio URL: " + audioUrl);
        }
    }
	/**
	 * Extracts a query parameter value from a URL.
	 *
	 * @param url The URL to extract the parameter from.
	 * @param paramName The name of the parameter to extract.
	 * @return The value of the parameter, or null if not found.
	 */
    private String extractQueryParam(String url, String paramName) {
        Matcher matcher = Pattern.compile("[?&]" + paramName + "=([^&]*)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
