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
// Importations pour ffmpeg et la manipulation de fichiers supprimées

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

		// Użyj globalnego rejestru kontenerów zamiast httpSourceManager.getContainerRegistry()
		MediaContainerRegistry registry = MediaContainerRegistry.DEFAULT;

		// Dopasuj do nowego API: find przyjmuje String (URL/ścieżkę)
		MediaContainerDescriptor descriptor = registry.find(audioUrl);

		// If no container is found, playback cannot proceed
		if (descriptor == null) {
			throw new FriendlyException("Could not find a container for the Amazon Music track.", FriendlyException.Severity.SUSPICIOUS, null);
		}

		// Create the HttpAudioTrack, passing the original trackInfo, the descriptor, and the source manager
		InternalAudioTrack httpTrack = new HttpAudioTrack(
			trackInfo,
			descriptor,
			httpSourceManager
		);

		// Process the track
		processDelegate(httpTrack, executor);
	}

	public void encode(DataOutput output) throws IOException {
		output.writeUTF(audioUrl != null ? audioUrl : "");
		output.writeUTF(isrc != null ? isrc : "");
		output.writeUTF(artworkUrl != null ? artworkUrl : "");
	}

	public static AmazonMusicAudioTrack decode(AudioTrackInfo trackInfo, DataInput input, AmazonMusicSourceManager sourceManager) throws IOException {
		String audioUrl = input.readUTF();
		String isrc = input.readUTF();
		String artworkUrl = input.readUTF();
		return new AmazonMusicAudioTrack(trackInfo, audioUrl, isrc, artworkUrl, sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return sourceManager;
	}
}