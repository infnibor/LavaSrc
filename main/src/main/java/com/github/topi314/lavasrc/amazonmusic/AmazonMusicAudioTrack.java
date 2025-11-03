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
		// ...existing code that prepares/obtains audioUrl and trackInfo...
		final String url = this.audioUrl; // upewnij się, że to finalna odszyfrowana URL do MP4/M4A
		final AudioTrackInfo info = getInfo();

		// Użyj HttpInterface z SourceManagera (ten sam, którego dotąd używałeś do HTTP)
		final HttpInterface httpInterface = sourceManager.getHttpInterface();

		try (SeekableInputStream stream = new PersistentHttpStream(httpInterface, new URI(url), null)) {
			// 1) Spróbuj wykryć kontener z danych
			MediaContainerDescriptor descriptor = MediaContainerDetection.detectContainer(
				MediaContainerRegistry.DEFAULT_REGISTRY, stream, info
			);

			if (descriptor == null) {
				// 2) Fallback po rozszerzeniu URL, jeśli z jakiegoś powodu detection nic nie zwróciło
				String lower = url.toLowerCase(Locale.ROOT);
				if (lower.contains(".mp4") || lower.contains(".m4a")) {
					descriptor = MediaContainerRegistry.DEFAULT_REGISTRY.byId("mp4");
				}
			}

			if (descriptor == null) {
				throw new FriendlyException("Nie udało się wykryć kontenera dla: " + url, Severity.SUSPICIOUS, null);
			}

			// 3) Utwórz delegowany track na podstawie descriptor + stream i uruchom go
			AudioTrack delegate = descriptor.createTrack(info, stream);
			if (delegate == null) {
				throw new FriendlyException("Kontener nie zwrócił ścieżki audio (descriptor=" + descriptor + ")", Severity.SUSPICIOUS, null);
			}

			// Przetwórz delegata przez standardowy executor
			processDelegate(delegate, executor);
		} catch (FriendlyException fe) {
			throw fe;
		} catch (Exception e) {
			throw new FriendlyException("Błąd odtwarzania strumienia HTTP: " + url, Severity.SUSPICIOUS, e);
		}
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		// ...existing code that ensures this.audioUrl is the final decrypted URL...
		final String url = this.audioUrl;
		final AudioTrackInfo info = this.trackInfo; // lub metoda getInfo(), jeśli taka istnieje

		// Dobierz kontener po rozszerzeniu URL
		String lower = url.toLowerCase(Locale.ROOT);
		String containerId = null;
		if (lower.contains(".mp4") || lower.contains(".m4a")) {
			containerId = "mp4";
		}
		if (containerId == null) {
			throw new FriendlyException("Nieznany kontener dla URL: " + url, Severity.SUSPICIOUS, null);
		}

		MediaContainerDescriptor descriptor = MediaContainerRegistry.DEFAULT_REGISTRY.find(containerId);
		if (descriptor == null) {
			throw new FriendlyException("Brak descriptor dla kontenera: " + containerId, Severity.SUSPICIOUS, null);
		}

		try (HttpInterface http = httpSourceManager.getHttpInterface();
		     SeekableInputStream stream = new PersistentHttpStream(http, new URI(url), null)) {

			// Utwórz delegowaną ścieżkę z descriptor + stream
			InternalAudioTrack delegate = (InternalAudioTrack) descriptor.createTrack(info, stream);
			if (delegate == null) {
				throw new FriendlyException("Kontener nie zwrócił ścieżki audio (descriptor=" + descriptor + ")", Severity.SUSPICIOUS, null);
			}

			processDelegate(delegate, executor);
		} catch (FriendlyException fe) {
			throw fe;
		} catch (Exception e) {
			throw new FriendlyException("Błąd odtwarzania strumienia HTTP: " + url, Severity.SUSPICIOUS, e);
		}
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