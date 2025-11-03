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
		// Zbuduj info wymagane przez HttpAudioTrack (identifier i uri ustaw na URL)
		AudioTrackInfo httpInfo = new AudioTrackInfo(
			base.title,
			base.author,
			base.length,
			url,
			base.isStream,
			url
		);

		HttpAudioTrack delegate = new HttpAudioTrack(httpInfo, httpSourceManager);
		processDelegate(delegate, executor);
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