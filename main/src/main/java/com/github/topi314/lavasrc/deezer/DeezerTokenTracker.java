package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavasrc.LavaSrcTools;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DeezerTokenTracker {

	private final DeezerAudioSourceManager sourceManager;


	private String arl;

	private Tokens tokens;


	private static final Logger log = LoggerFactory.getLogger(DeezerTokenTracker.class);
	private static final int MAX_ARL_FETCH_ATTEMPTS = 3;
	private String arlUrlCache = null;
	private Instant arlLastFetch = null;


	public DeezerTokenTracker(DeezerAudioSourceManager sourceManager, String arl) {
		this.sourceManager = sourceManager;
		if (arl == null || arl.isEmpty()) {
			throw new NullPointerException("Deezer arl must be set");
		}
		this.arl = arl;
	}

	public String getArl() {
		if (arl != null && arl.startsWith("http")) {
			return fetchArlFromUrl();
		}
		return this.arl;
	}

	private String fetchArlFromUrl() {
		if (arlUrlCache != null && arlLastFetch != null && Instant.now().isBefore(arlLastFetch.plus(30, ChronoUnit.MINUTES))) {
			return arlUrlCache;
		}
		int attempts = 0;
		while (attempts < MAX_ARL_FETCH_ATTEMPTS) {
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				HttpGet get = new HttpGet(this.arl);
				var response = client.execute(get);
				int status = response.getStatusLine().getStatusCode();
				if (status == 200) {
					String value = EntityUtils.toString(response.getEntity()).trim();
					if (isValidArl(value)) {
						arlUrlCache = value;
						arlLastFetch = Instant.now();
						log.info("Fetched arl from URL: {}", value);
						return value;
					} else {
						log.warn("Fetched arl value is invalid: {}", value);
					}
				} else {
					log.warn("Failed to fetch arl from URL, status: {}", status);
				}
			} catch (Exception e) {
				log.error("Error fetching arl from URL: {}", this.arl, e);
			}
			attempts++;
		}
		throw new IllegalStateException("Failed to fetch arl from URL after " + MAX_ARL_FETCH_ATTEMPTS + " attempts");
	}

	public boolean isValidArl(String value) {
		return value != null && value.length() == 192 && value.matches("[a-zA-Z0-9]+$");
	}

	public void invalidateArlCache() {
		arlUrlCache = null;
		arlLastFetch = null;
		log.info("arl cache has been cleared");
	}

	private void refreshSession() throws IOException {
		try (var httpInterface = sourceManager.getHttpInterface()) {
			var cookieStore = new BasicCookieStore();
			httpInterface.getContext().setCookieStore(cookieStore);
			httpInterface.getContext().setRequestConfig(
				RequestConfig.copy(httpInterface.getContext().getRequestConfig())
					.setCookieSpec(CookieSpecs.STANDARD)
					.build()
			);

			var getUserToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");
			var json = LavaSrcTools.fetchResponseAsJson(httpInterface, getUserToken);
			DeezerAudioSourceManager.checkResponse(json, "Failed to get user token");

			String sessionID = null;
			String dzrUniqId = null;
			for (var cookie : cookieStore.getCookies()) {
				switch (cookie.getName()) {
					case "sid":
						sessionID = cookie.getValue();
						break;
					case "dzr_uniq_id":
						dzrUniqId = cookie.getValue();
						break;
				}
			}

			if (sessionID == null) {
				throw new IOException("Failed to find sid cookie");
			}
			if (dzrUniqId == null) {
				throw new IOException("Failed to find dzr uniq id cookie");
			}

			this.tokens = new Tokens(
				sessionID,
				dzrUniqId,
				json.get("results").get("checkForm").text(),
				json.get("results").get("USER").get("OPTIONS").get("license_token").text(),
				Instant.now().plus(3600, ChronoUnit.SECONDS)
			);
		}
	}

	public Tokens getTokens() throws IOException {
		if (this.tokens == null || Instant.now().isAfter(this.tokens.expireAt)) {
			this.refreshSession();
		}
		return this.tokens;
	}

	public void setArl(String arl) {
		if (arl == null || arl.isEmpty()) {
			throw new NullPointerException("Deezer arl must be set");
		}
		if (!arl.equals(this.arl)) {
			invalidateArlCache();
		}
		this.arl = arl;
	}

	public static class Tokens {
		public String sessionId;
		public String dzrUniqId;
		public String api;
		public String license;
		public Instant expireAt;

		public Tokens(String sessionId, String dzrUniqId, String api, String license, Instant expireAt) {
			this.sessionId = sessionId;
			this.dzrUniqId = dzrUniqId;
			this.api = api;
			this.license = license;
			this.expireAt = expireAt;
		}
	}
}
