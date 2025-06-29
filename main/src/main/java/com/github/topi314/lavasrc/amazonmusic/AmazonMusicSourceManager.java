package com.github.topi314.lavasrc.amazonmusic;

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

public class AmazonMusicSourceManager implements AudioSourceManager {
    private static final String AMAZON_MUSIC_URL_REGEX = "https?://music\\.amazon\\.(com|de|co\\.uk|fr|it|es|co\\.jp|ca|com\\.au)/detail/([a-zA-Z0-9\\-_]+)";
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
        try {
            String trackId = matcher.group(2);
            TrackJson trackJson = fetchTrackInfo(trackId);
            if (trackJson == null) {
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
            return new AmazonMusicAudioTrack(info, trackJson.audioUrl, this);
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Amazon Music track", FriendlyException.Severity.FAULT, e);
        }
    }

    private static class TrackJson {
        String title;
        String artist;
        long duration;
        String audioUrl;
    }

    private TrackJson fetchTrackInfo(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "track/" + trackId : apiUrl + "/track/" + trackId;
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
        return result;
    }

    private static String extractJsonString(String json, String key, String def) {
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1) : def;
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
        ((AmazonMusicAudioTrack) track).encode(output);
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return AmazonMusicAudioTrack.decode(trackInfo, input, this);
    }

    @Override
    public void shutdown() {
    }
}
