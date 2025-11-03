package com.github.topi314.lavasrc.amazonmusic;

import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;

import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;

public class AmazonMusicAudioTrack extends DelegatedAudioTrack {
    private final String audioUrl;
    private final String isrc;
    private final AmazonMusicSourceManager sourceManager;
    private final HttpAudioSourceManager httpSourceManager;
    private final String artworkUrl;

    public AmazonMusicAudioTrack(AudioTrackInfo trackInfo, String audioUrl, String isrc, String artworkUrl, AmazonMusicSourceManager sourceManager) {
        super(trackInfo);
        this.audioUrl = audioUrl;
        this.isrc = isrc;
        this.artworkUrl = artworkUrl;
        this.sourceManager = sourceManager;
        this.httpSourceManager = new HttpAudioSourceManager();
    }

    public String getIsrc() {
        return isrc;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (audioUrl == null || audioUrl.isEmpty()) {
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Missing or invalid audioUrl for track: " + trackInfo.identifier);
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Full trackInfo: " + trackInfo);
            throw new IllegalStateException("Missing or invalid audioUrl for Amazon Music track.");
        }

        System.out.println("[AmazonMusicAudioTrack] [INFO] Processing track with audioUrl: " + audioUrl);

        MediaContainerDescriptor descriptor = null;

        String lowerUrl = audioUrl.toLowerCase();
        if (lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".m4a")) {
            Path tempInput = Files.createTempFile("amzn_input_", lowerUrl.endsWith(".mp4") ? ".mp4" : ".m4a");
            try (java.io.InputStream in = new URL(audioUrl).openStream()) {
                Files.copy(in, tempInput, StandardCopyOption.REPLACE_EXISTING);
            }
            Path tempMp3 = Files.createTempFile("amzn_output_", ".mp3");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", tempInput.toAbsolutePath().toString(),
                    "-vn", "-acodec", "libmp3lame", "-ar", "44100", "-ac", "2", "-ab", "192k",
                    tempMp3.toAbsolutePath().toString()
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                    }
                }
                int exitCode = proc.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("FFmpeg conversion failed with exit code " + exitCode);
                }
                // Odtwarzaj plik mp3 lokalnie
                InternalAudioTrack httpTrack = new HttpAudioTrack(
                    new AudioTrackInfo(
                        trackInfo.title,
                        trackInfo.author,
                        trackInfo.length,
                        trackInfo.identifier,
                        trackInfo.isStream,
                        tempMp3.toUri().toString()
                    ),
                    descriptor,
                    httpSourceManager
                );
                processDelegate(httpTrack, executor);
            } finally {
                try { Files.deleteIfExists(tempInput); } catch (Exception ignore) {}
                try { Files.deleteIfExists(tempMp3); } catch (Exception ignore) {}
            }
        } else {
            InternalAudioTrack httpTrack = new HttpAudioTrack(
                new AudioTrackInfo(
                    trackInfo.title,
                    trackInfo.author,
                    trackInfo.length,
                    trackInfo.identifier,
                    trackInfo.isStream,
                    audioUrl
                ),
                descriptor,
                httpSourceManager
            );
            processDelegate(httpTrack, executor);
        }
    }

    public void encode(DataOutput output) throws IOException {
        output.writeUTF(audioUrl != null ? audioUrl : "");
    }

    public static AmazonMusicAudioTrack decode(AudioTrackInfo trackInfo, DataInput input, AmazonMusicSourceManager sourceManager) throws IOException {
        String audioUrl = input.readUTF();
        // Placeholder values for ISRC and artworkUrl since they are not encoded
        String isrc = null;
        String artworkUrl = null;
        return new AmazonMusicAudioTrack(trackInfo, audioUrl, isrc, artworkUrl, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
