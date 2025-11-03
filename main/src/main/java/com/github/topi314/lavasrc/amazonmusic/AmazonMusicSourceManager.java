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
import java.util.Arrays;

public class AmazonMusicSourceManager implements AudioSourceManager {
    private static final String AMAZON_MUSIC_URL_REGEX =
	    "https?:\\/\\/music\\.amazon\\.[a-z\\.]+\\/(tracks?|albums?|playlists?|artists?|podcast|episodes?|lyrics)\\/([A-Za-z0-9\\-_]+)(?:\\?[^\\s\"]*)?";
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
        // Remove additional parameters from the URL, leaving only the base link
        String cleanedIdentifier = reference.identifier.split("\\?")[0];
        Matcher matcher = AMAZON_MUSIC_URL_PATTERN.matcher(cleanedIdentifier);
        if (!matcher.matches()) {
            return null;
        }

        String type = matcher.group(1);
        String id = matcher.group(2);

	    id = id.split("[^a-zA-Z0-9\\-_]", 2)[0];

        // Added declaration of the trackAsin variable
        String trackAsin = reference.identifier.contains("?") ? extractQueryParam(reference.identifier, "trackAsin") : null;

        try {
            // Handle podcasts
            if ("podcast".equals(type)) {
                AlbumJson podcastJson = fetchPodcastInfo(id);
                if (podcastJson == null || podcastJson.tracks == null || podcastJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : podcastJson.tracks) {
                    AudioUrlResult audioResult = track.audioUrl != null
                        ? new AudioUrlResult(track.audioUrl, track.artworkUrl, track.isrc)
                        : fetchAudioUrlFromStreamUrls(track.id);
                    if (audioResult == null || audioResult.audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for podcast track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioResult.audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioResult.audioUrl, audioResult.isrc, audioResult.artworkUrl, this));
                }
                return new BasicAudioPlaylist(podcastJson.title != null ? podcastJson.title : "Podcast", tracks, null, false);
            }

            // Handle episodes
            if ("episode".equals(type)) {
                TrackJson episodeJson = fetchEpisodeInfo(id);
                if (episodeJson == null) return AudioReference.NO_TRACK;
                AudioUrlResult audioResult = episodeJson.audioUrl != null
                    ? new AudioUrlResult(episodeJson.audioUrl, episodeJson.artworkUrl, episodeJson.isrc)
                    : fetchAudioUrlFromStreamUrls(episodeJson.id);
                if (audioResult == null || audioResult.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for episode: " + episodeJson.id);
                    return AudioReference.NO_TRACK;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    episodeJson.title,
                    episodeJson.artist,
                    episodeJson.duration,
                    episodeJson.id != null ? episodeJson.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioResult.audioUrl);
                return new AmazonMusicAudioTrack(info, audioResult.audioUrl, audioResult.isrc, audioResult.artworkUrl, this);
            }

            // Handle song lyrics
            if ("lyrics".equals(type)) {
                String lyrics = fetchLyrics(id);
                if (lyrics == null) {
                    System.err.println("[AmazonMusic] [ERROR] No lyrics found for track: " + id);
                    return null;
                }
                System.out.println("Lyrics for track " + id + ":");
                System.out.println(lyrics);
                return null; // Lyrics are not playable
            }

            // Handle account information
            if ("account".equals(type)) {
                String accountInfo = fetchAccountInfo();
                if (accountInfo == null) {
                    System.err.println("[AmazonMusic] [ERROR] Failed to fetch account information.");
                    return null;
                }
                System.out.println("Account Information:");
                System.out.println(accountInfo);
                return null; // Account information is not playable
            }

            // Handle album with trackAsin (single track from album)
            if ("albums".equals(type) && trackAsin != null) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                TrackJson foundTrack = null;
                for (TrackJson track : albumJson.tracks) {
                    if (trackAsin.equals(track.id) || (track.asin != null && trackAsin.equals(track.asin))) {
                        foundTrack = track;
                        break;
                    }
                }
                if (foundTrack == null) {
                    System.err.println("[AmazonMusic] [ERROR] No track with id/asIn=" + trackAsin + " found in album " + id);
                    return null;
                }
                if (foundTrack.audioUrl == null) {
                    AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(foundTrack.id != null ? foundTrack.id : trackAsin);
                    if (audioResult != null) {
                        foundTrack.audioUrl = audioResult.audioUrl;
                        foundTrack.artworkUrl = audioResult.artworkUrl;
                        foundTrack.isrc = audioResult.isrc;
                    }
                }
                if (foundTrack.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is still null after stream_urls for track: " + foundTrack.id);
                    return AudioReference.NO_TRACK;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    foundTrack.title,
                    foundTrack.artist,
                    foundTrack.duration,
                    foundTrack.id != null ? foundTrack.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + foundTrack.audioUrl);
                return new AmazonMusicAudioTrack(info, foundTrack.audioUrl, foundTrack.isrc, foundTrack.artworkUrl, this);
            } else if ("tracks".equals(type)) {
                String trackId = id;
                TrackJson trackJson = fetchTrackInfo(trackId);
                if (trackJson == null) return AudioReference.NO_TRACK;
                if (trackJson.audioUrl == null) {
                    AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(trackJson.id != null ? trackJson.id : trackId);
                    if (audioResult != null) {
                        trackJson.audioUrl = audioResult.audioUrl;
                        trackJson.artworkUrl = audioResult.artworkUrl;
                        trackJson.isrc = audioResult.isrc;
                    }
                }
                if (trackJson.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for track: " + trackJson.id);
                    return AudioReference.NO_TRACK;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    trackJson.title,
                    trackJson.artist,
                    trackJson.duration,
                    trackJson.id != null ? trackJson.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + trackJson.audioUrl);
                AmazonMusicAudioTrack track = new AmazonMusicAudioTrack(info, trackJson.audioUrl, trackJson.isrc, trackJson.artworkUrl, this);
                return track;
            } else if ("albums".equals(type)) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : albumJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for album track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    AmazonMusicAudioTrack audioTrack = new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this);
                    tracks.add(audioTrack);
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(albumJson.title != null ? albumJson.title : "Amazon Music Album", tracks, null, false);
            } else if ("playlists".equals(type)) {
                PlaylistJson playlistJson = fetchPlaylistInfo(id);
                if (playlistJson == null || playlistJson.tracks == null || playlistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : playlistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for playlist track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(playlistJson.title != null ? playlistJson.title : "Amazon Music Playlist", tracks, null, false);
            } else if ("artists".equals(type)) {
                ArtistJson artistJson = fetchArtistInfo(id);
                if (artistJson == null || artistJson.tracks == null || artistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : artistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for artist track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(artistJson.name != null ? artistJson.name : "Amazon Music Artist", tracks, null, false);
            } else {
                System.err.println("[AmazonMusic] [ERROR] Unsupported type: " + type);
                return AudioReference.NO_TRACK;
            }
        } catch (IOException e) {
            System.err.println("[AmazonMusic] [ERROR] Network error while loading item: " + e.getMessage());
            e.printStackTrace();
            throw new FriendlyException("Failed to load Amazon Music item due to network error", FriendlyException.Severity.FAULT, e);
        } catch (Exception e) {
            System.err.println("[AmazonMusic] [ERROR] Unexpected error while loading item: " + e.getMessage());
            e.printStackTrace();
            throw new FriendlyException("Failed to load Amazon Music item due to unexpected error", FriendlyException.Severity.FAULT, e);
        }
    }

    /**
     * Searches for tracks on Amazon Music.
     *
     * @param query The search query.
     * @param limit The maximum number of results to return.
     * @return List of found AudioTrack objects (never null).
     */
    public List<AudioTrack> search(String query, int limit) {
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
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
            // Try to extract artist from object or string (object first)
            t.artist = extractArtistFlexible(obj, "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            // Add parsing for asin, image, isrc
            t.asin = extractJsonString(obj, "asin", null);
            t.artworkUrl = extractJsonString(obj, "image", null);
            t.isrc = extractJsonString(obj, "isrc", null);
            if (t.audioUrl == null || !isSupportedAudioFormat(t.audioUrl)) {
                System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null or unsupported for search track: " + t.id);
                continue;
            }
            AudioTrackInfo info = new AudioTrackInfo(
                t.title,
                t.artist,
                t.duration,
                t.id != null ? t.id : "",
                false,
                t.audioUrl
            );
            System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + t.audioUrl);
            tracks.add(new AmazonMusicAudioTrack(info, t.audioUrl, t.isrc, t.artworkUrl, this));
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

    /**
     * Fetches community playlist information.
     */
    private PlaylistJson fetchCommunityPlaylistInfo(String playlistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "community_playlist?id=" + playlistId : apiUrl + "/community_playlist?id=" + playlistId;
        return fetchTracksContainer(url, PlaylistJson.class);
    }

    /**
     * Fetches episode information.
     */
    private TrackJson fetchEpisodeInfo(String episodeId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "episode?id=" + episodeId : apiUrl + "/episode?id=" + episodeId;
        return fetchTracksContainer(url, TrackJson.class);
    }

    /**
     * Fetches podcast information.
     */
    private AlbumJson fetchPodcastInfo(String podcastId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "podcast?id=" + podcastId : apiUrl + "/podcast?id=" + podcastId;
        return fetchTracksContainer(url, AlbumJson.class);
    }

    /**
     * Fetches lyrics for a track.
     */
    private String fetchLyrics(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "lyrics?id=" + trackId : apiUrl + "/lyrics?id=" + trackId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
        return content.toString();
    }

    /**
     * Fetches account information.
     */
    private String fetchAccountInfo() throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "account" : apiUrl + "/account";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
        return content.toString();
    }

    private <T> T fetchTracksContainer(String url, Class<T> clazz) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
        List<TrackJson> tracks = new ArrayList<>();
        for (String item : items) {
            String obj = item;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            TrackJson t = new TrackJson();
            t.title = extractJsonString(obj, "title", "Unknown Title");
            t.artist = extractArtistFlexible(obj, "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            t.asin = extractJsonString(obj, "asin", null);
            t.artworkUrl = extractJsonString(obj, "image", null);
            t.isrc = extractJsonString(obj, "isrc", null);
            tracks.add(t);
        }
        return tracks.toArray(new TrackJson[0]);
    }

    private TrackJson fetchTrackInfo(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "track?id=" + trackId : apiUrl + "/track?id=" + trackId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
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
        // Skup się na sekcji "data" (jeśli istnieje), inaczej czytaj z całego JSON
        String dataObj = extractObject(json, "data");
        String scope = dataObj != null ? dataObj : json;

        TrackJson result = new TrackJson();
        // Pola podstawowe
        result.title = extractJsonString(scope, "title", "Unknown Title");
        result.artist = extractArtistFlexible(scope, "Unknown Artist");
        result.duration = extractJsonLong(scope, "duration", 0L);
        result.audioUrl = extractJsonString(scope, "audioUrl", null);
        result.id = extractJsonString(scope, "id", null);
        result.asin = extractJsonString(scope, "asin", null);
        result.artworkUrl = extractJsonString(scope, "image", null);
        result.isrc = extractJsonString(scope, "isrc", null);
        result.url = extractJsonString(scope, "url", null);

        // Pola rozszerzone
        result.trackNum = (int) extractJsonLong(scope, "track_num", 0L);
        result.discNum = (int) extractJsonLong(scope, "disc_num", 0L);
        result.genre = extractJsonString(scope, "genre", null);
        result.releaseDate = extractJsonLong(scope, "release_date", 0L);
        result.explicit = extractJsonBoolean(scope, "explicit", false);
        result.hasLyrics = extractJsonBoolean(scope, "has_lyrics", false);
        result.languages = extractJsonString(scope, "languages", null);
        result.primaryArtistName = extractJsonString(scope, "primary_artist_name", null);
        result.isFree = extractJsonBoolean(scope, "is_free", false);
        result.isPrime = extractJsonBoolean(scope, "is_prime", false);
        result.isMusicSubscription = extractJsonBoolean(scope, "is_music_subscription", false);
        result.isSonicRush = extractJsonBoolean(scope, "is_sonic_rush", false);
        result.type = extractJsonString(scope, "type", null);
        result.assetQualities = extractJsonStringArray(scope, "asset_qualities");

        // Album
        String albumObj = extractObject(scope, "album");
        if (albumObj != null) {
            result.albumTitle = extractJsonString(albumObj, "title", null);
            result.albumAsin = extractJsonString(albumObj, "asin", null);
            result.albumId = extractJsonString(albumObj, "id", null);
            result.albumImage = extractJsonString(albumObj, "image", null);
            result.albumUrl = extractJsonString(albumObj, "url", null);
        }

        // Artist
        String artistObj = extractObject(scope, "artist");
        if (artistObj != null) {
            // nazwa artysty już wyciągnięta; nadpisz jeśli to precyzyjniejsza wartość
            String artistName = extractJsonString(artistObj, "name", null);
            if (artistName != null) result.artist = artistName;
            result.artistId = extractJsonString(artistObj, "id", null);
            result.artistAsin = extractJsonString(artistObj, "asin", null);
            result.artistUrl = extractJsonString(artistObj, "url", null);
        }

        return result;
    }

    // Extract artist from object ("artist":{"name":"..."}) or string ("artist":"..."), prefer object
    private static String extractArtistFlexible(String json, String def) {
        Matcher objMatcher = Pattern.compile("\"artist\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"(.*?)\"").matcher(json);
        if (objMatcher.find()) return objMatcher.group(1);
        Matcher strMatcher = Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\"").matcher(json);
        return strMatcher.find() ? strMatcher.group(1) : def;
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
     * Fetches audioUrl, artworkUrl i isrc z /stream_urls?id={track_id}.
     */
    private AudioUrlResult fetchAudioUrlFromStreamUrls(String trackId) throws IOException {
		if (trackId == null) {
			System.err.println("[AmazonMusic] [ERROR] Track ID is null.");
			return null;
		}

		String base = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
		String url = base + "stream_urls?id=" + trackId;
		System.out.println("[AmazonMusic] [DEBUG] Fetching stream URLs from: " + url);

		// Retry na 5xx (główna ścieżka)
		final int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			HttpResponse res = httpGet(url);
			System.out.println("[AmazonMusic] [DEBUG] Response status: " + res.status + ", attempt " + attempt + "/" + maxAttempts);
			System.out.println("[AmazonMusic] [DEBUG] Response JSON: " + res.body);

			if (res.status == 200) {
				AudioUrlResult parsed = parseAudioFromJson(res.body);
				if (parsed != null && parsed.audioUrl != null) {
					return parsed;
				}
				System.err.println("[AmazonMusic] [ERROR] Could not extract audio from stream_urls payload.");
				break;
			}

			if (res.status >= 500 && res.status < 600) {
				try { Thread.sleep(300L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
				continue;
			}
			break;
		}

		// Fallback 1: alternatywne endpointy znane z kompatybilnych API
		String[] alt = new String[] { "stream_url", "stream" };
		for (String endpoint : alt) {
			String altUrl = base + endpoint + "?id=" + trackId;
			System.out.println("[AmazonMusic] [DEBUG] Fallback fetching from: " + altUrl);
			AudioUrlResult parsed = tryFetchAndParse(altUrl, 2);
			if (parsed != null && parsed.audioUrl != null) return parsed;
		}

		// Fallback 2: spróbuj z /track?id=..., czasem endpoint track zawiera te same pola (urls/data)
		AudioUrlResult trackFallback = tryFetchAudioViaTrackEndpoint(trackId);
		if (trackFallback != null && trackFallback.audioUrl != null) {
			return trackFallback;
		}

		// Fallback 3: jeśli mamy asset_qualities z /track, spróbuj wymusić quality na /stream_urls
		try {
			TrackJson meta = fetchTrackInfo(trackId);
			List<String> qualities = new ArrayList<>();
			if (meta != null && meta.assetQualities != null && meta.assetQualities.length > 0) {
				qualities.addAll(Arrays.asList(meta.assetQualities));
			}
			if (qualities.isEmpty()) {
				qualities.addAll(Arrays.asList("MP3", "CD", "HD", "ULTRA_HD"));
			}
			for (String q : qualities) {
				String qUrl = base + "stream_urls?id=" + trackId + "&quality=" + encode(q);
				System.out.println("[AmazonMusic] [DEBUG] Quality fallback fetching from: " + qUrl);
				AudioUrlResult parsed = tryFetchAndParse(qUrl, 2);
				if (parsed != null && parsed.audioUrl != null) return parsed;
			}
			// Ostatnia próba: wymuś codec=mp3
			String codecUrl = base + "stream_urls?id=" + trackId + "&codec=mp3";
			System.out.println("[AmazonMusic] [DEBUG] Codec fallback fetching from: " + codecUrl);
			AudioUrlResult parsed = tryFetchAndParse(codecUrl, 2);
			if (parsed != null && parsed.audioUrl != null) return parsed;
		} catch (Exception ignored) {
			// cichy fallback – brak meta nie powinien przerywać
		}

		System.err.println("[AmazonMusic] [ERROR] audioUrl is still null after processing stream endpoints for track: " + trackId);
		return null;
	}

	// Wspólne parsowanie audioUrl/artwork/isrc z JSON (działa dla stream_urls, stream_url, stream, a także track z polami urls/data)
	private AudioUrlResult parseAudioFromJson(String json) {
		if (json == null || json.isEmpty()) return null;

		// 1) Spróbuj standardowego pola urls.{high|medium|low}
		String audioUrl = null;
		String artworkUrl = null;
		String isrc = null;

		Matcher urlsMatcher = Pattern.compile("\"urls\"\\s*:\\s*\\{(.*?)\\}").matcher(json);
		if (urlsMatcher.find()) {
			String urlsContent = urlsMatcher.group(1);
			String[] keys = new String[] { "high", "medium", "low" };
			for (String k : keys) {
				String u = extractJsonString(urlsContent, k);
				if (u != null && formatScore(u) > 0) {
					audioUrl = u;
					break;
				}
			}
		}

		// 1a) Preview URL (jeśli backend zwraca tylko metadane)
		if (audioUrl == null) {
			String preview = extractJsonString(json, "preview_url");
			if (preview == null) preview = extractJsonString(json, "previewUrl");
			if (preview != null && isSupportedAudioFormat(preview)) {
				audioUrl = preview;
			}
		}

		// 2) Jeśli brak lub niepewne, spróbuj data[] z base_url – pomijaj DRM i mp4
		if (audioUrl == null || audioUrl.isEmpty()) {
			String bestUrl = null;
			String bestArtwork = null;
			String bestIsrc = null;
			int bestScore = -1;

			Matcher dataArrayMatcher = Pattern.compile("\"data\"\\s*:\\s*\\[(.*?)\\](?!\\s*,)").matcher(json);
			if (dataArrayMatcher.find()) {
				String dataArray = dataArrayMatcher.group(1);
				Matcher entryMatcher = Pattern.compile("\\{(.*?)\\}(,|$)").matcher(dataArray);
				while (entryMatcher.find()) {
					String entry = entryMatcher.group(1);

					// pomiń oczywisty DRM
					boolean hasDrm = entry.contains("\"content_protection\"")
						|| entry.contains("\"pssh\"")
						|| entry.matches("(?is).*\"value\"\\s*:\\s*\"cenc\".*");
					if (hasDrm) {
						System.out.println("[AmazonMusic] [DEBUG] Skipping DRM-protected representation.");
						continue;
					}

					String baseUrl = extractJsonString(entry, "base_url");
					String entryArtwork = extractJsonString(entry, "image");
					String entryIsrc = extractJsonString(entry, "isrc");

					int score = formatScore(baseUrl); // preferuj mp3 > m3u8 > ogg > flac > wav; mp4 == 0
					if (baseUrl != null && score > bestScore) {
						bestScore = score;
						bestUrl = baseUrl;
						bestArtwork = entryArtwork;
						bestIsrc = entryIsrc;
					}
				}
			}

			if (bestUrl != null) {
				System.out.println("[AmazonMusic] [DEBUG] Final candidate (non-DRM, playable): " + bestUrl);
				audioUrl = bestUrl;
				artworkUrl = bestArtwork;
				isrc = bestIsrc;
			}
		}

		// 3) Ostatnia deska ratunku: wyszukaj pierwszy bezpośredni URL audio po rozszerzeniu (bez mp4)
		if (audioUrl == null || audioUrl.isEmpty()) {
			audioUrl = extractUrlByExtensions(json, "mp3", "m3u8", "ogg", "flac", "wav", "m4a");
		}

		if (audioUrl == null || audioUrl.isEmpty()) {
			return null;
		}
		return new AudioUrlResult(audioUrl, artworkUrl, isrc);
	}

    // Prosty helper HTTP czytający również error stream
	private static class HttpResponse {
		final int status;
		final String body;
		HttpResponse(int status, String body) {
			this.status = status;
			this.body = body;
		}
	}

	private HttpResponse httpGet(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
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
		return new HttpResponse(status, content.toString());
	}

	// Pomocnicze: fetch z retry i parsowaniem
	private AudioUrlResult tryFetchAndParse(String url, int attempts) throws IOException {
		for (int i = 1; i <= attempts; i++) {
			HttpResponse res = httpGet(url);
			System.out.println("[AmazonMusic] [DEBUG] Fallback response status: " + res.status + " (attempt " + i + "/" + attempts + ")");
			System.out.println("[AmazonMusic] [DEBUG] Fallback response JSON: " + res.body);
			if (res.status == 200) {
				AudioUrlResult parsed = parseAudioFromJson(res.body);
				if (parsed != null && parsed.audioUrl != null) return parsed;
			}
			if (res.status >= 500 && res.status < 600) {
				try { Thread.sleep(200L * i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
				continue;
			}
			break;
		}
		return null;
	}

    /**
	 * Extracts a JSON string value for a given key using regex (no JSON parser used).
	 */
	private String extractJsonString(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"").matcher(json);
		return matcher.find() ? matcher.group(1) : null;
	}

    private String extractJsonString(String json, String key, String def) {
        String value = extractJsonString(json, key);
        return value != null ? value : def;
    }

    private long extractJsonLong(String json, String key, long def) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : def;
    }

    // NOWOŚĆ: parsowanie boolean
    private boolean extractJsonBoolean(String json, String key, boolean def) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)").matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : def;
    }

    // NOWOŚĆ: wycięcie pod-obiektu JSON: key: { ... }
    private String extractObject(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx == -1) return null;
        int braceStart = json.indexOf('{', keyIdx);
        if (braceStart == -1) return null;
        int depth = 0;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(braceStart, i + 1);
                }
            }
        }
        return null;
    }

    // NOWOŚĆ: wyciąganie parametru zapytania z URL
    private String extractQueryParam(String url, String name) {
        int q = url.indexOf('?');
        if (q == -1) return null;
        String query = url.substring(q + 1);
        String[] parts = query.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            if (name.equals(key)) {
                String val = eq >= 0 ? part.substring(eq + 1) : "";
                try {
                    return java.net.URLDecoder.decode(val, "UTF-8");
                } catch (Exception e) {
                    return val;
                }
            }
        }
        return null;
    }

    // NOWOŚĆ: parser tablicy stringów: "key": ["A","B",...]
    private String[] extractJsonStringArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]").matcher(json);
        if (!m.find()) return new String[0];
        String inside = m.group(1).trim();
        if (inside.isEmpty()) return new String[0];
        String[] parts = inside.split("\\s*,\\s*");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            Matcher s = Pattern.compile("^\"(.*?)\"$").matcher(p.trim());
            if (s.find()) out.add(s.group(1));
        }
        return out.toArray(new String[0]);
    }

    // NOWOŚĆ: sprawdzanie obsługiwanych rozszerzeń URL audio
    // UWAGA: usuwamy mp4 – brak wsparcia w używanym Lavaplayer, unika NPE z containerTrackFactory
    private static boolean isSupportedAudioFormat(String audioUrl) {
        if (audioUrl == null) return false;
        return audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav|m3u8)(\\?.*)?$");
        //                          ^^^ usunięto mp4
    }

    // NOWOŚĆ: wyszukanie pierwszego URL-a po rozszerzeniu w surowym JSON
    private String extractUrlByExtensions(String json, String... extensions) {
        String[] quoted = Arrays.stream(extensions).map(Pattern::quote).toArray(String[]::new);
        String joined = String.join("|", quoted);
        Matcher m = Pattern.compile("(https?:\\/\\/[^\"\\s>]+\\.(?:" + joined + "))(?:\\?[^\"\\s]*)?").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // NOWOŚĆ: scoring formatów preferowanych przez Lavaplayer (bez mp4)
    private static int formatScore(String url) {
        if (url == null) return 0;
        String u = url.toLowerCase();
        if (u.contains(".mp3")) return 100;
        if (u.contains(".m3u8")) return 95;
        if (u.contains(".ogg")) return 90;
        if (u.contains(".flac")) return 85;
        if (u.contains(".wav")) return 80;
        // mp4 nieobsługiwane – 0 punktów
        return 0;
    }

    // Dodaj klasę TrackJson
    private static class TrackJson {
        String id;
        String title;
        String artist;
        long duration;
        String audioUrl;
        String asin;
        String artworkUrl;
        String isrc;

        // Metadane rozszerzone z /track -> data
        String url;
        int trackNum;
        int discNum;
        String genre;
        long releaseDate;
        boolean explicit;
        boolean hasLyrics;
        String languages;
        String primaryArtistName;
        boolean isFree;
        boolean isPrime;
        boolean isMusicSubscription;
        boolean isSonicRush;
        String type;
        String[] assetQualities;

        // Album
        String albumTitle;
        String albumAsin;
        String albumId;
        String albumImage;
        String albumUrl;

        // Artist
        String artistId;
        String artistAsin;
        String artistUrl;
    }

    // Klasa pomocnicza do zwracania audioUrl, artworkUrl i isrc
    private static class AudioUrlResult {
        public final String audioUrl;
        public final String artworkUrl;
        public final String isrc;
        public AudioUrlResult(String audioUrl, String artworkUrl, String isrc) {
            this.audioUrl = audioUrl;
            this.artworkUrl = artworkUrl;
            this.isrc = isrc;
        }
    }

    private AudioUrlResult tryFetchAudioViaTrackEndpoint(String trackId) throws IOException {
		if (trackId == null) return null;
		String base = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";

		// 1) Szybka ścieżka: metadane z /track
		try {
			TrackJson meta = fetchTrackInfo(trackId);
			if (meta != null) {
				String candidate = meta.audioUrl;
				// czasem pole "url" jest bezpośrednim odnośnikiem do audio
				if (candidate == null && meta.url != null && isSupportedAudioFormat(meta.url)) {
					candidate = meta.url;
				}
				if (candidate != null && isSupportedAudioFormat(candidate)) {
					String art = meta.artworkUrl != null ? meta.artworkUrl : meta.albumImage;
					return new AudioUrlResult(candidate, art, meta.isrc);
				}
			}
		} catch (Exception ignored) {
			// ignoruj, spróbuj parsowania surowego JSON
		}

		// 2) Surowe /track i wspólny parser JSON
		AudioUrlResult parsed = tryFetchAndParse(base + "track?id=" + trackId, 2);
		if (parsed != null && parsed.audioUrl != null) return parsed;

		return null;
	}
}
