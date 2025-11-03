package com.github.topi314.lavasrc.amazonmusic;

import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe;

import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;
import java.util.Locale;

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
		final String url = this.audioUrl;
		if (url == null || url.isEmpty()) {
			throw new FriendlyException("Brak URL do strumienia audio", FriendlyException.Severity.SUSPICIOUS, null);
		}

		AudioTrackInfo base = getInfo();
		AudioTrackInfo httpInfo = new AudioTrackInfo(
			base.title,
			base.author,
			base.length,
			url,
			base.isStream,
			url
		);

		MediaContainerDescriptor descriptor = selectDescriptorForUrl(url);
		HttpAudioTrack delegate = new HttpAudioTrack(httpInfo, descriptor, httpSourceManager);
		processDelegate(delegate, executor);
	}

	// Prosty wybór kontenera po rozszerzeniu URL (domyślnie mp4/m4a).
	private MediaContainerDescriptor selectDescriptorForUrl(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		String id;
		if (lower.contains(".mp3")) {
			id = "mp3";
		} else if (lower.contains(".webm")) {
			id = "webm";
		} else if (lower.contains(".ogg") || lower.contains(".oga")) {
			id = "ogg";
		} else {
			// domyślnie traktuj jako mp4/m4a (AAC)
			id = "mp4";
		}

		MediaContainerProbe probe = MediaContainerRegistry.DEFAULT_REGISTRY.find(id);
		if (probe == null) {
			throw new FriendlyException("Brak obsługi kontenera: " + id, FriendlyException.Severity.SUSPICIOUS, null);
		}
		return new MediaContainerDescriptor(probe, null);
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