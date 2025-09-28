package com.github.topi314.lavasrc.deezer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.MockedStatic;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.HttpClients;
import org.mockito.Mockito;
import java.io.ByteArrayInputStream;

class DeezerTokenTrackerTest {

	@Test
	void testIsValidArl() {
		DeezerTokenTracker tracker = Mockito.mock(DeezerTokenTracker.class, Mockito.CALLS_REAL_METHODS);
		Assertions.assertTrue(tracker.isValidArl("a".repeat(192)));
		Assertions.assertFalse(tracker.isValidArl("b".repeat(191)));
		Assertions.assertFalse(tracker.isValidArl("!@#".repeat(64)));
	}

	@Test
	void testFetchArlFromUrlMocked() throws Exception {
		String validArl = "a".repeat(192);
		String arlUrl = "https://luke.gg/arl";
		DeezerTokenTracker tracker = new DeezerTokenTracker(null, arlUrl);

		// Mock HTTP client and response
		CloseableHttpClient httpClient = Mockito.mock(CloseableHttpClient.class);
		HttpGet httpGet = new HttpGet(arlUrl);
		CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
		StatusLine statusLine = Mockito.mock(StatusLine.class);
		HttpEntity entity = Mockito.mock(HttpEntity.class);

		Mockito.when(statusLine.getStatusCode()).thenReturn(200);
		Mockito.when(response.getStatusLine()).thenReturn(statusLine);
		Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(validArl.getBytes()));
		Mockito.when(response.getEntity()).thenReturn(entity);
		Mockito.when(httpClient.execute(Mockito.any(HttpGet.class))).thenReturn(response);

		try (MockedStatic<HttpClients> mocked = Mockito.mockStatic(HttpClients.class)) {
			mocked.when(HttpClients::createDefault).thenReturn(httpClient);
			String result = tracker.getArl();
			Assertions.assertEquals(validArl, result);
		}
	}

}
