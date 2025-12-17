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
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class DeezerTokenTracker {

	private final DeezerAudioSourceManager sourceManager;


	private String arl;

	private Tokens tokens;


	private static final Logger log = LoggerFactory.getLogger(DeezerTokenTracker.class);
	private static final int MAX_ARL_FETCH_ATTEMPTS = 3;
	private String arlUrlCache = null;
	private Instant arlLastFetch = null;
	private static final Pattern ARL_PATTERN = Pattern.compile("[a-zA-Z0-9]{192}");


	public DeezerTokenTracker(DeezerAudioSourceManager sourceManager, String arl) {
		this.sourceManager = sourceManager;
		if (arl == null || arl.isEmpty()) {
			throw new NullPointerException("Deezer arl must be set");
		}
		this.arl = arl;
	}

	public synchronized String getArl() {
		if (arl != null && arl.startsWith("http")) {
			return fetchArlFromUrl(false);
		}
		return this.arl;
	}

	private String fetchArlFromUrl(boolean forceRotation) {
		if (!forceRotation && arlUrlCache != null && arlLastFetch != null && Instant.now().isBefore(arlLastFetch.plus(30, ChronoUnit.MINUTES))) {
			return arlUrlCache;
		}
		int attempts = 0;
		while (attempts < MAX_ARL_FETCH_ATTEMPTS) {
			var requestUrl = buildArlRequestUrl(forceRotation);
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				var get = new HttpGet(requestUrl);
				try (var response = client.execute(get)) {
					int status = response.getStatusLine().getStatusCode();
					if (status == 200) {
						String value = EntityUtils.toString(response.getEntity()).trim();
						if (isValidArl(value)) {
							arlUrlCache = value;
							arlLastFetch = Instant.now();
							log.info(forceRotation ? "Fetched rotated arl from URL" : "Fetched arl from URL: {}", forceRotation ? "<hidden>" : value);
							return value;
						} else {
							log.warn("Fetched arl value is invalid: {}", value);
						}
					} else {
						log.warn("Failed to fetch{} arl from URL, status: {}", forceRotation ? " rotated" : "", status);
					}
				}
			} catch (Exception e) {
				log.error("Error fetching{} arl from URL: {}", forceRotation ? " rotated" : "", this.arl, e);
			}
			attempts++;
		}
		throw new IllegalStateException("Failed to fetch" + (forceRotation ? " rotated" : "") + " arl from URL after " + MAX_ARL_FETCH_ATTEMPTS + " attempts");
	}

	private String buildArlRequestUrl(boolean forceRotation) {
		if (!forceRotation) {
			return this.arl;
		}
		try {
			return new URIBuilder(this.arl).setParameter("force", "1").build().toString();
		} catch (URISyntaxException e) {
			log.warn("Failed to append force parameter to arl URL {}, falling back to manual concat", this.arl, e);
			return this.arl + (this.arl.contains("?") ? "&" : "?") + "force=1";
		}
	}

	public synchronized void invalidateArlCache() {
		arlUrlCache = null;
		arlLastFetch = null;
		log.info("arl cache has been cleared");
	}

	public synchronized String forceRotateArl() {
		if (this.arl == null || this.arl.isEmpty()) {
			throw new IllegalStateException("Cannot rotate empty arl configuration");
		}
		invalidateArlCache();
		if (!this.arl.startsWith("http")) {
			log.debug("Static arl configured, returning existing value after cache clear");
			return this.arl;
		}
		return fetchArlFromUrl(true);
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

	public boolean isValidArl(String value) {
		return value != null && ARL_PATTERN.matcher(value).matches();
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
