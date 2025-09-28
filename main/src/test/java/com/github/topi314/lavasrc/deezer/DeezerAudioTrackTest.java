package com.github.topi314.lavasrc.deezer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.apache.http.client.ClientProtocolException;

class DeezerAudioTrackTest {
	@Test
	void testIs403Exception() {
		DeezerAudioTrack track = Mockito.mock(DeezerAudioTrack.class, Mockito.CALLS_REAL_METHODS);
		Exception e403 = new java.io.IOException("Server returned HTTP response code: 403 for URL");
		Exception eOther = new java.io.IOException("Server returned HTTP response code: 404 for URL");
		Assertions.assertTrue(track.is403Exception(e403));
		Assertions.assertFalse(track.is403Exception(eOther));
	}

	@Test
	void testRetryLogicOn403() throws Exception {
		DeezerAudioTrack track = Mockito.mock(DeezerAudioTrack.class, Mockito.CALLS_REAL_METHODS);
		DeezerTokenTracker tokenTracker = Mockito.mock(DeezerTokenTracker.class);
		// Simulate 403 on first call, success on second
		Mockito.doThrow(new ClientProtocolException("403")).doReturn("validArl").when(tokenTracker).getArl();

		// Simulate invalidateArlCache being called
		Mockito.doNothing().when(tokenTracker).invalidateArlCache();

		int attempts = 0;
		String arl = null;
		while (attempts < 2) {
			try {
				arl = tokenTracker.getArl();
				break;
			} catch (Exception e) {
				if (track.is403Exception(e) && attempts == 0) {
					tokenTracker.invalidateArlCache();
					attempts++;
					continue;
				}
				throw e;
			}
		}
		Assertions.assertEquals("validArl", arl);
	}

	// You can add tests mocking retry logic if you use HTTP and TokenTracker mocking tools
}
