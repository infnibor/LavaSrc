package com.github.topi314.lavasrc.amazonmusic;

import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;

public class AmazonMusicAudioTrack extends DelegatedAudioTrack {
    private final String audioUrl;
    private final AmazonMusicSourceManager sourceManager;
    private final HttpAudioSourceManager httpSourceManager;
    private String artworkUrl;

    public AmazonMusicAudioTrack(AudioTrackInfo trackInfo, String audioUrl, AmazonMusicSourceManager sourceManager) {
        super(trackInfo);
        this.audioUrl = audioUrl;
        this.sourceManager = sourceManager;
        this.httpSourceManager = new HttpAudioSourceManager();
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (audioUrl == null || audioUrl.isEmpty()) {
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Missing audioUrl for track: " + trackInfo.identifier);
            throw new IllegalStateException("Missing audioUrl for Amazon Music track.");
        }

        // Check if audioUrl has a supported format
        if (!audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav)(\\?.*)?$")) {
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Unsupported file format for audioUrl: " + audioUrl);
            throw new FriendlyException("Unsupported file format for Amazon Music track: " + audioUrl, FriendlyException.Severity.COMMON, null);
        }

        System.out.println("[AmazonMusicAudioTrack] [INFO] Processing track with audioUrl: " + audioUrl);

        // Create an internal HTTP track and pass it to the delegate
        InternalAudioTrack httpTrack = new HttpAudioTrack(
                new AudioTrackInfo(
                        trackInfo.title != null ? trackInfo.title : "Unknown Title",
                        trackInfo.author != null ? trackInfo.author : "Unknown Artist",
                        trackInfo.length,
                        trackInfo.identifier,
                        trackInfo.isStream,
                        audioUrl
                ),
                null,
                httpSourceManager
        );
        processDelegate(httpTrack, executor);
    }

    public void encode(DataOutput output) throws IOException {
        output.writeUTF(audioUrl != null ? audioUrl : "");
    }

    public static AmazonMusicAudioTrack decode(AudioTrackInfo trackInfo, DataInput input, AmazonMusicSourceManager sourceManager) throws IOException {
        String audioUrl = input.readUTF();
        return new AmazonMusicAudioTrack(trackInfo, audioUrl, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = artworkUrl;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }
}
